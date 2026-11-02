package com.example.copilot.aiassistant.interfaces.rest.evaluation;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestUsers;
import com.example.copilot.aiassistant.application.evaluation.EvalRunner;
import com.example.copilot.aiassistant.domain.chat.EvaluationReport;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.eval.CaseResult;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalDataset;
import com.example.copilot.aiassistant.domain.eval.EvalJudgePrompt;
import com.example.copilot.aiassistant.domain.eval.EvalMetrics;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import com.example.copilot.aiassistant.domain.eval.JudgeVerdict;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.application.config.ConfigService;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

class EvalControllerTest extends IntegrationTestBase {

    private static final String KEY = "instruction.contact-prep";
    private static final String BASE = "/api/ai-assistant/admin";
    private static final String LIST_URL = BASE + "/configs/" + KEY + "/evaluations";
    private static final String ADMIN = "EEE22222";
    private static final String OFFICER = "ICRM00028";

    @MockitoBean EvalRunner runner;

    @Autowired EvalRunRepository runs;
    @Autowired ConfigService configService;
    @Autowired AssistantConfigRepository configs;
    @Autowired SeedLoader seedLoader;
    @Autowired MongoTemplate mongo;
    @Autowired Clock clock;
    @Autowired EvalDataset dataset;

    @BeforeEach
    void setUp() {
        mongo.remove(new Query(), EvalRun.COLLECTION);
    }

    @AfterEach
    void tearDown() {
        mongo.remove(new Query(), EvalRun.COLLECTION);
        configs.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    private ResultActions send(String url, String body, String user) throws Exception {
        var request = post(url).with(TestUsers.as(user)).with(csrf());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions read(String url) throws Exception {
        return mockMvc.perform(get(url).with(TestUsers.as(ADMIN)));
    }

    private long draftDocVersion() {
        return configService.createDraft(KEY, ADMIN).getDocVersion();
    }

    private static EvalCaseRecord caseRecord(String id) {
        CaseResult side = new CaseResult("ANSWERED", null, "answer", List.of(new EvaluationReport.ToolCall("t", "SUCCESS")),
                2, 100, 50, 150, 1200, List.of("123"), true, true, List.of(), List.of(),
                new JudgeVerdict(false, List.of(new JudgeVerdict.RubricResult("rubric text", true)), "ok", null, 40),
                true, true, "ROUTER", "instruction.contact-prep");
        return new EvalCaseRecord(id, "g", "question?", false, "instruction.contact-prep", side, side);
    }

    private String insertRun(EvalStatus status, Instant startedAt, String hash, List<EvalCaseRecord> cases) {
        EvalMetrics m = new EvalMetrics(1, 1, 1, 0, 0, 0, 1, 1, 1, 150, 100, 50, 1200);
        EvalSummary summary = status == EvalStatus.COMPLETED ? new EvalSummary(m, m, 80) : null;
        Instant finished = status == EvalStatus.RUNNING ? null : startedAt.plusSeconds(30);
        return runs.insert(new EvalRun(null, KEY, 2, 7L, startedAt, List.of(new PromptVersion(KEY, 1)), hash, status,
                null, "someone", startedAt, clock.instant(), finished, 16, cases.size(), summary, cases, 0L));
    }

    @Test
    void startReturns202() throws Exception {
        long docVersion = draftDocVersion();

        send(BASE + "/configs/" + KEY + "/evaluations", "{\"docVersion\":" + docVersion + "}", ADMIN)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.totalCases").value(dataset.cases().size()))
                .andExpect(jsonPath("$.runId", matchesPattern("^[0-9a-f]{24}$")));
    }

    @Test
    void startNeedsEdit() throws Exception {
        long docVersion = draftDocVersion();
        String url = BASE + "/configs/" + KEY + "/evaluations";

        send(url, "{\"docVersion\":" + docVersion + "}", OFFICER).andExpect(status().isForbidden());
        send(url, "{}", ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
        send(url, "{\"docVersion\":" + docVersion + ",\"certNo\":\"C9\"}", ADMIN)
                .andExpect(status().isAccepted());
    }

    @Test
    void runningConflictHasDetails() throws Exception {
        long docVersion = draftDocVersion();
        insertRun(EvalStatus.RUNNING, clock.instant(), dataset.hash(), List.of());

        send(BASE + "/configs/" + KEY + "/evaluations", "{\"docVersion\":" + docVersion + "}", ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_EVAL_RUNNING"))
                .andExpect(jsonPath("$.details[0].configKey").value(KEY));
    }

    @Test
    void recentListsTenNewestWithoutCases() throws Exception {
        Instant base = clock.instant().minusSeconds(3600);
        for (int i = 0; i < 12; i++) {
            String hash = i == 5 ? "f".repeat(64) : dataset.hash();
            insertRun(EvalStatus.COMPLETED, base.plusSeconds(i * 60L), hash, List.of(caseRecord("A1")));
        }

        String body = read(LIST_URL).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].cases").doesNotExist())
                .andExpect(jsonPath("$[0].baselineVersions").doesNotExist())
                .andExpect(jsonPath("$[0].datasetHash").doesNotExist())
                .andExpect(jsonPath("$[0].startedAt", endsWith("+08:00")))
                .andExpect(jsonPath("$[0].candidateUpdatedAt", endsWith("+08:00")))
                .andExpect(jsonPath("$[0].datasetChanged").value(false))
                .andExpect(jsonPath("$[0].summary.judgeTokens").value(80))
                .andExpect(jsonPath("$[0].summary.candidate.total").value(1))
                .andReturn().getResponse().getContentAsString();

        // Newest first: index 11 is [0]; index 5 is [6]; the oldest two (0, 1) are cut.
        org.assertj.core.api.Assertions.assertThat(body).contains("\"datasetChanged\":true");
        read(LIST_URL).andExpect(jsonPath("$[6].datasetChanged").value(true))
                .andExpect(jsonPath("$[5].datasetChanged").value(false));
    }

    @Test
    void currentIs204WhenIdle() throws Exception {
        read(BASE + "/evaluations/current").andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void currentReturnsProgress() throws Exception {
        String id = insertRun(EvalStatus.RUNNING, clock.instant(), dataset.hash(), List.of(caseRecord("A1")));

        read(BASE + "/evaluations/current").andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(id))
                .andExpect(jsonPath("$.configKey").value(KEY))
                .andExpect(jsonPath("$.triggeredBy").value("someone"))
                .andExpect(jsonPath("$.totalCases").value(16))
                .andExpect(jsonPath("$.completedCases").value(1))
                .andExpect(jsonPath("$.startedAt", endsWith("+08:00")));
    }

    @Test
    void getRunDetail() throws Exception {
        String id = insertRun(EvalStatus.COMPLETED, clock.instant(), dataset.hash(), List.of(caseRecord("A1")));

        read(BASE + "/evaluations/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(id))
                .andExpect(jsonPath("$.configKey").value(KEY))
                .andExpect(jsonPath("$.datasetChanged").value(false))
                .andExpect(jsonPath("$.cases[0].caseId").value("A1"))
                .andExpect(jsonPath("$.cases[0].baseline.judge.rubric[0].text").value("rubric text"))
                .andExpect(jsonPath("$.cases[0].candidate.judge.rubric[0].met").value(true));

        read(BASE + "/evaluations/not-an-id").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
        read(BASE + "/evaluations/64b7f0f0f0f0f0f0f0f0f0f0").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_EVAL_NOT_FOUND"));
    }

    @Test
    void casesEndpoint() throws Exception {
        read(BASE + "/evaluation-cases").andExpect(status().isOk())
                .andExpect(jsonPath("$.cases", hasSize(dataset.cases().size())))
                .andExpect(jsonPath("$.datasetHash", matchesPattern("^[0-9a-f]{64}$")))
                .andExpect(jsonPath("$.cases[0].id").exists());

        mockMvc.perform(get(BASE + "/evaluation-cases").with(TestUsers.as(OFFICER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsesNeverContainJudgePrompt() throws Exception {
        String firstLine = EvalJudgePrompt.TEXT.lines().findFirst().orElseThrow();
        long docVersion = draftDocVersion();
        String id = insertRun(EvalStatus.COMPLETED, clock.instant().minusSeconds(60), dataset.hash(),
                List.of(caseRecord("A1")));
        List<ResultActions> all = new ArrayList<>();
        all.add(send(BASE + "/configs/" + KEY + "/evaluations", "{\"docVersion\":" + docVersion + "}", ADMIN));
        all.add(read(LIST_URL));
        all.add(read(BASE + "/evaluations/current"));
        all.add(read(BASE + "/evaluations/" + id));
        all.add(read(BASE + "/evaluation-cases"));
        for (ResultActions a : all) {
            a.andExpect(content().string(not(containsString(firstLine))));
        }
    }
}
