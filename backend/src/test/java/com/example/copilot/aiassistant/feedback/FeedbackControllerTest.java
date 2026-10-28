package com.example.copilot.aiassistant.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestUsers;
import com.example.copilot.aiassistant.audit.AuditRecord;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class FeedbackControllerTest extends IntegrationTestBase {

    private static final String RATE_URL = "/api/ai-assistant/chat/{id}/feedback";
    private static final String STATS_URL = "/api/ai-assistant/admin/configs/{key}/feedback-stats";
    private static final String KEY = "instruction.contact-prep";
    private static final String OTHER_KEY = "instruction.other-key";
    private static final String MARKER = "fb-test";

    @Autowired MongoTemplate mongo;

    /** Every document this class inserts carries the marker question, so cleanup never touches other tests. */
    @AfterEach
    void cleanUp() {
        mongo.remove(new Query(Criteria.where("question").is(MARKER)), AuditRecord.COLLECTION);
    }

    private Document audit(String requestId, String userId, String outcome, Date createdAt) {
        return new Document("requestId", requestId).append("userId", userId).append("roleKey", "r")
                .append("accessLevel", "FULL").append("question", MARKER)
                .append("outcome", outcome).append("toolCalls", List.of()).append("durationMs", 5)
                .append("createdAt", createdAt);
    }

    private Document withVersion(Document d, String key, int version, String feedback) {
        return d.append("promptVersions", List.of(new Document("configKey", key).append("version", version)))
                .append("routeSource", "ROUTER").append("feedback", feedback).append("feedbackAt", feedback == null ? null : new Date());
    }

    private void insert(Document d) {
        mongo.getCollection(AuditRecord.COLLECTION).insertOne(d);
    }

    private List<Document> find(String requestId) {
        return mongo.getCollection(AuditRecord.COLLECTION).find(new Document("requestId", requestId))
                .sort(new Document("createdAt", 1)).into(new ArrayList<>());
    }

    private ResultActions rate(String id, RequestPostProcessor who, String body) throws Exception {
        return mockMvc.perform(put(RATE_URL, id).with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void ratesOwnAnswer() throws Exception {
        insert(audit("a1b2c3d4", "fc.officer", "ANSWERED", new Date()));
        rate("a1b2c3d4", TestUsers.as("fc.officer"), "{\"rating\":\"UP\"}").andExpect(status().isNoContent());
        Document d = find("a1b2c3d4").get(0);
        assertThat(d.getString("feedback")).isEqualTo("UP");
        assertThat(d.get("feedbackAt")).isNotNull();
    }

    @Test
    void switchAndCancel() throws Exception {
        insert(audit("a1b2c3d5", "fc.officer", "ANSWERED", new Date()));
        rate("a1b2c3d5", TestUsers.as("fc.officer"), "{\"rating\":\"UP\"}").andExpect(status().isNoContent());
        rate("a1b2c3d5", TestUsers.as("fc.officer"), "{\"rating\":\"DOWN\"}").andExpect(status().isNoContent());
        assertThat(find("a1b2c3d5").get(0).getString("feedback")).isEqualTo("DOWN");
        rate("a1b2c3d5", TestUsers.as("fc.officer"), "{\"rating\":null}").andExpect(status().isNoContent());
        assertThat(find("a1b2c3d5").get(0).getString("feedback")).isNull();
    }

    @Test
    void othersAnswerFailedAndUnknownAreSame404() throws Exception {
        insert(audit("a1b2c3d6", "ao.officer", "ANSWERED", new Date()));
        insert(audit("a1b2c3d7", "fc.officer", "FAILED", new Date()));
        for (String id : List.of("a1b2c3d6", "a1b2c3d7", "a1b2c3d8")) {
            rate(id, TestUsers.as("fc.officer"), "{\"rating\":\"UP\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AI_FEEDBACK_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("這則回答已無法評分"));
        }
        assertThat(find("a1b2c3d6").get(0).get("feedback")).isNull();
    }

    @Test
    void newestOfDuplicatesIsUpdated() throws Exception {
        insert(audit("a1b2c3d9", "fc.officer", "ANSWERED", new Date(1_000_000L)));
        insert(audit("a1b2c3d9", "fc.officer", "ANSWERED", new Date(2_000_000L)));
        rate("a1b2c3d9", TestUsers.as("fc.officer"), "{\"rating\":\"DOWN\"}").andExpect(status().isNoContent());
        List<Document> docs = find("a1b2c3d9");
        assertThat(docs.get(0).get("feedback")).isNull();
        assertThat(docs.get(1).getString("feedback")).isEqualTo("DOWN");
    }

    @Test
    void invalidInputIs400() throws Exception {
        rate("XYZ", TestUsers.as("fc.officer"), "{\"rating\":\"UP\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
        rate("a1b2c3da", TestUsers.as("fc.officer"), "{\"rating\":\"MAYBE\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
    }

    @Test
    void legacyAuditWithoutNewFieldsCanBeRated() throws Exception {
        insert(audit("a1b2c3db", "fc.officer", "ANSWERED", new Date()));
        assertThat(find("a1b2c3db").get(0).containsKey("feedback")).isFalse();
        rate("a1b2c3db", TestUsers.as("fc.officer"), "{\"rating\":\"UP\"}").andExpect(status().isNoContent());
        assertThat(find("a1b2c3db").get(0).getString("feedback")).isEqualTo("UP");
    }

    @Test
    void statsPerVersion() throws Exception {
        insert(withVersion(audit("b0000001", "fb-user", "ANSWERED", new Date()), KEY, 2, "UP"));
        insert(withVersion(audit("b0000002", "fb-user", "ANSWERED", new Date()), KEY, 2, "UP"));
        insert(withVersion(audit("b0000003", "fb-user", "ANSWERED", new Date()), KEY, 2, "DOWN"));
        insert(withVersion(audit("b0000004", "fb-user", "ANSWERED", new Date()), KEY, 3, "UP"));
        insert(withVersion(audit("b0000005", "fb-user", "ANSWERED", new Date()), KEY, 3, null));
        insert(withVersion(audit("b0000006", "fb-user", "ANSWERED", new Date()), OTHER_KEY, 2, "UP"));
        mockMvc.perform(get(STATS_URL, KEY).with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[0].up").value(1))
                .andExpect(jsonPath("$[0].down").value(0))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].up").value(2))
                .andExpect(jsonPath("$[1].down").value(1));
    }

    /** One answer per route source (plus a legacy record without the field) for the same key and version. */
    private void insertRouteFixtures(String key, int version, String prefix) {
        String[][] rows = {{"ROUTER", "UP"}, {"CHIP", "DOWN"}, {"FALLBACK", "UP"}, {null, "UP"}};
        for (int i = 0; i < rows.length; i++) {
            Document d = withVersion(audit(prefix + i, "fb-user", "ANSWERED", new Date()), key, version, rows[i][1]);
            if (rows[i][0] == null) {
                d.remove("routeSource");
            } else {
                d.put("routeSource", rows[i][0]);
            }
            insert(d);
        }
    }

    @Test
    void instructionStatsCountOnlyRoutedAndChip() throws Exception {
        insertRouteFixtures(KEY, 91, "c000000");
        mockMvc.perform(get(STATS_URL, KEY).with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(91))
                .andExpect(jsonPath("$[0].up").value(1))
                .andExpect(jsonPath("$[0].down").value(1));
    }

    @Test
    void systemMainStatsCountEverything() throws Exception {
        insertRouteFixtures("system.main", 91, "d000000");
        mockMvc.perform(get(STATS_URL, "system.main").with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.version==91)].up").value(3))
                .andExpect(jsonPath("$[?(@.version==91)].down").value(1));
    }

    @Test
    void statsUnknownKeyIs404() throws Exception {
        mockMvc.perform(get(STATS_URL, "instruction.no-such-key").with(TestUsers.as("it.admin")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AI_CONFIG_NOT_FOUND"));
    }

    @Test
    void statsNeedsConfigRead() throws Exception {
        mockMvc.perform(get(STATS_URL, KEY).with(TestUsers.as("fc.officer")))
                .andExpect(status().isForbidden());
    }
}
