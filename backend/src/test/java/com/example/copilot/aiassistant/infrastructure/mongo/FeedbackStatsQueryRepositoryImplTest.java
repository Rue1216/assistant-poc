package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.feedback.VersionFeedback;
import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

class FeedbackStatsQueryRepositoryImplTest extends IntegrationTestBase {

    private static final String MARKER = "fb-stats-repo-test";
    private static final String KEY = "instruction.stats-repo-test";

    @Autowired MongoTemplate mongo;

    @AfterEach
    void cleanUp() {
        mongo.remove(new Query(Criteria.where("question").is(MARKER)), AuditRecord.COLLECTION);
    }

    private void insert(String traceId, String routeSource, String feedback) {
        Document d = new Document("traceId", traceId).append("userId", "u").append("roleKey", "r")
                .append("accessLevel", "FULL").append("question", MARKER).append("outcome", "ANSWERED")
                .append("toolCalls", List.of()).append("durationMs", 5).append("createdAt", new Date())
                .append("promptVersions", List.of(new Document("configKey", KEY).append("version", 7)))
                .append("feedback", feedback).append("feedbackAt", new Date());
        if (routeSource != null) {
            d.append("routeSource", routeSource);
        }
        mongo.getCollection(AuditRecord.COLLECTION).insertOne(d);
    }

    @Test
    void routedOnlyFalseCountsEveryAnswer() {
        insert("e0000000", "ROUTER", "UP");
        insert("e0000001", "CHIP", "DOWN");
        insert("e0000002", "FALLBACK", "UP");
        insert("e0000003", null, "UP");
        FeedbackStatsQueryRepositoryImpl repository = new FeedbackStatsQueryRepositoryImpl(mongo);

        assertThat(repository.versionStats(KEY, false)).containsExactly(new VersionFeedback(7, 3, 1));
        assertThat(repository.versionStats(KEY, true)).containsExactly(new VersionFeedback(7, 1, 1));
    }
}
