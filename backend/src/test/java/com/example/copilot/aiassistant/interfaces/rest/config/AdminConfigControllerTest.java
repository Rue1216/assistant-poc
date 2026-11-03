package com.example.copilot.aiassistant.interfaces.rest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestUsers;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigMongoRepository;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminConfigControllerTest extends IntegrationTestBase {

    private static final String BASE = "/api/ai-assistant/admin/configs";
    private static final String KEY = "instruction.contact-prep";
    private static final String DRAFT_URL = BASE + "/" + KEY + "/draft";
    private static final String PUBLISH_URL = BASE + "/" + KEY + "/publish";
    private static final String VERSIONS_URL = BASE + "/" + KEY + "/versions";
    private static final String INVALID_CONTENT = "## 其他\n只有一個章節";
    private static final String ADMIN = "EEE22222";
    private static final String WORKING_COPY_PATH = "$[?(@.configKey=='" + KEY + "')].workingCopy";

    @Autowired AssistantConfigMongoRepository repository;
    @Autowired SeedLoader seedLoader;
    @Autowired Clock clock;

    @AfterEach
    void restoreSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request.with(TestUsers.as(ADMIN)).with(csrf()));
    }

    private ResultActions read(String url) throws Exception {
        return mockMvc.perform(get(url).with(TestUsers.as(ADMIN)));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private String createDraft() throws Exception {
        return body(call(post(DRAFT_URL), null).andExpect(status().isCreated()));
    }

    private static long docVersion(String json) {
        return ((Number) JsonPath.read(json, "$.docVersion")).longValue();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private String save(String draftJson, String newContent, String saveMode) throws Exception {
        String content = newContent != null ? newContent : JsonPath.read(draftJson, "$.content");
        String request = "{\"name\":\"聯繫準備\",\"content\":" + quote(content) + ",\"changeNote\":\"n\","
                + "\"saveMode\":\"" + saveMode + "\",\"docVersion\":" + docVersion(draftJson) + "}";
        return body(call(put(DRAFT_URL), request).andExpect(status().isOk()));
    }

    private String publishNow(String json) throws Exception {
        return body(call(post(PUBLISH_URL),
                "{\"publishMode\":\"IMMEDIATE\",\"docVersion\":" + docVersion(json) + "}")
                .andExpect(status().isOk()));
    }

    private String schedule(String draftJson) throws Exception {
        String at = clock.instant().plus(Duration.ofDays(3)).toString();
        return body(call(post(PUBLISH_URL), "{\"publishMode\":\"SCHEDULED\",\"scheduledAt\":\"" + at
                + "\",\"docVersion\":" + docVersion(draftJson) + "}").andExpect(status().isOk()));
    }

    @Test
    void fcOfficerGets403() throws Exception {
        mockMvc.perform(get(BASE).with(TestUsers.as("ICRM00028")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_FORBIDDEN"));
        mockMvc.perform(post(DRAFT_URL).with(TestUsers.as("ICRM00028")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidConfigKeyIs400() throws Exception {
        read(BASE + "/Bad_Key/versions")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
    }

    @Test
    void listShowsPublishedAndWorkingCopy() throws Exception {
        read(BASE).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath(WORKING_COPY_PATH).value(contains((Object) null)));
        createDraft();
        read(BASE).andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath(WORKING_COPY_PATH + ".status").value("DRAFT"))
                .andExpect(jsonPath("$[?(@.configKey=='" + KEY + "')].published.version").value(1));
    }

    @Test
    void createKeyReturns201AndRejectsDuplicates() throws Exception {
        String request = "{\"configKey\":\"instruction.new-topic\",\"configType\":\"SYSTEM_INSTRUCTION\","
                + "\"name\":\"新分類\"}";
        call(post(BASE), request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1));
        call(post(BASE), request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_INVALID_STATE"));
        call(post(BASE), "{\"configKey\":\"instruction.x\",\"configType\":\"SYSTEM_PROMPT\",\"name\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
    }

    @Test
    void saveAndPublishFlow() throws Exception {
        String saved = save(createDraft(), null, "KEEP_STATUS");
        publishNow(saved);
        read(VERSIONS_URL).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[1].status").value("ARCHIVED"))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void staleDocVersionOnSaveIs409() throws Exception {
        String draft = createDraft();
        save(draft, null, "KEEP_STATUS");
        call(put(DRAFT_URL), "{\"name\":\"x\",\"content\":\"c\",\"saveMode\":\"DRAFT\",\"docVersion\":"
                + docVersion(draft) + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_CONFLICT"));
    }

    @Test
    void nameWithLineBreakOrControlCharacterIs400OnCreateAndSave() throws Exception {
        call(post(BASE), "{\"configKey\":\"instruction.new-topic\",\"configType\":\"SYSTEM_INSTRUCTION\","
                + "\"name\":\"新\\n分類\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
        String draft = createDraft();
        for (String bad : new String[] {"a\\nb", "a\\rb", "a\\u0000b", "a\\u2028b"}) {
            call(put(DRAFT_URL), "{\"name\":\"" + bad + "\",\"content\":\"c\",\"saveMode\":\"DRAFT\","
                    + "\"docVersion\":" + docVersion(draft) + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                    .andExpect(jsonPath("$.details[0].field").value("name"));
        }
    }

    @Test
    void saveWithoutSaveModeIs400() throws Exception {
        String draft = createDraft();
        call(put(DRAFT_URL), "{\"name\":\"x\",\"content\":\"c\",\"docVersion\":" + docVersion(draft) + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
    }

    @Test
    void scheduledPublishRequiresScheduledAt() throws Exception {
        long doc = docVersion(createDraft());
        call(post(PUBLISH_URL), "{\"publishMode\":\"SCHEDULED\",\"docVersion\":" + doc + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("scheduledAt"));
        call(post(PUBLISH_URL),
                "{\"publishMode\":\"SCHEDULED\",\"scheduledAt\":\"not-a-date\",\"docVersion\":" + doc + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("scheduledAt"));
    }

    @Test
    void validateEndpointReturnsErrors() throws Exception {
        save(createDraft(), INVALID_CONTENT, "KEEP_STATUS");
        call(post(DRAFT_URL + "/validate"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].rule").value("REQUIRED_SECTION"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void validateEndpointReportsValid() throws Exception {
        createDraft();
        call(post(DRAFT_URL + "/validate"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void publishInvalidReturns422WithDetails() throws Exception {
        String saved = save(createDraft(), INVALID_CONTENT, "KEEP_STATUS");
        call(post(PUBLISH_URL), "{\"publishMode\":\"IMMEDIATE\",\"docVersion\":" + docVersion(saved) + "}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].rule").value("REQUIRED_SECTION"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @Test
    void applyOverwriteHandshake() throws Exception {
        long wcDoc = docVersion(schedule(createDraft()));
        String applyUrl = VERSIONS_URL + "/1/apply";
        call(post(applyUrl), "{\"workingCopyDocVersion\":" + wcDoc + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_OVERWRITE_PENDING"))
                .andExpect(jsonPath("$.details[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.details[0].docVersion").value(wcDoc))
                .andExpect(jsonPath("$.details[0].scheduledAt").value(endsWith("+08:00")))
                .andExpect(jsonPath("$.details[0].updatedAt").value(endsWith("+08:00")));
        call(post(applyUrl), "{\"workingCopyDocVersion\":" + wcDoc + ",\"confirmOverwrite\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.basedOnVersion").value(1));
        read(VERSIONS_URL)
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].status").value("DISCARDED"));
    }

    @Test
    void applyWithoutWorkingCopyReturnsDraft() throws Exception {
        call(post(VERSIONS_URL + "/1/apply"), "{\"workingCopyDocVersion\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.basedOnVersion").value(1));
        call(post(VERSIONS_URL + "/99/apply"), "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_NOT_FOUND"));
    }

    @Test
    void deleteDraftKeepsDocumentAsDiscarded() throws Exception {
        long doc = docVersion(createDraft());
        call(delete(DRAFT_URL).param("docVersion", String.valueOf(doc)), null)
                .andExpect(status().isNoContent());
        read(VERSIONS_URL).andExpect(jsonPath("$[0].status").value("DISCARDED"));
        call(delete(DRAFT_URL).param("docVersion", "0"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_CONFLICT"));
    }

    @Test
    void deleteScheduledIsInvalidState() throws Exception {
        long doc = docVersion(schedule(createDraft()));
        call(delete(DRAFT_URL).param("docVersion", String.valueOf(doc)), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_INVALID_STATE"));
    }

    @Test
    void archivedVersionKeepsItsTimeAfterNewPublish() throws Exception {
        MvcResult before = read(VERSIONS_URL).andReturn();
        String v1UpdatedAt = JsonPath.read(before.getResponse().getContentAsString(), "$[0].updatedAt");
        publishNow(createDraft());
        read(VERSIONS_URL)
                .andExpect(jsonPath("$[1].status").value("ARCHIVED"))
                .andExpect(jsonPath("$[1].updatedAt").value(v1UpdatedAt));
    }

    @Test
    void versionViewIncludesExampleQuestions() throws Exception {
        read(VERSIONS_URL + "/1").andExpect(status().isOk())
                .andExpect(jsonPath("$.exampleQuestions", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.configKey").value(KEY))
                .andExpect(jsonPath("$.docVersion").value(0))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(content().string(not(containsString("不可覆寫"))));
        read(BASE + "/system.main/versions/1")
                .andExpect(jsonPath("$.exampleQuestions", hasSize(0)));
        read(VERSIONS_URL + "/99")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_NOT_FOUND"));
    }

    @Test
    void scheduledAtIsSerializedWithOffset() throws Exception {
        String scheduled = schedule(createDraft());
        assertThat((String) JsonPath.read(scheduled, "$.scheduledAt")).endsWith("+08:00");
        assertThat((String) JsonPath.read(scheduled, "$.updatedAt")).endsWith("+08:00");
        read(BASE).andExpect(jsonPath(WORKING_COPY_PATH + ".scheduledAt").value(contains(endsWith("+08:00"))));
    }
}
