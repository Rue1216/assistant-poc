package com.example.copilot.aiassistant.application.evaluation;

import com.example.copilot.aiassistant.application.command.evaluation.EvalRunCommandUseCase;
import static com.example.copilot.aiassistant.application.chat.ScriptedChatModel.answer;
import static com.example.copilot.aiassistant.application.chat.ScriptedChatModel.text;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.chat.ScriptedChatModel;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import com.example.copilot.aiassistant.domain.eval.EvalCase;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import com.example.copilot.aiassistant.infrastructure.mongo.AiAssistantCollections;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Shared fixtures of the {@link EvalRunner} tests: scripted model, run insertion, case and reply builders. */
abstract class EvalRunnerTestBase extends IntegrationTestBase {

    static final int ANSWER_TOKENS = 100;
    static final int JUDGE_TOKENS = 10;

    @Autowired EvalRunner runner;
    @Autowired EvalRunRepository runs;
    @Autowired MongoTemplate mongo;
    @Autowired Clock clock;

    final ScriptedChatModel scripted = new ScriptedChatModel();

    @BeforeEach
    void wireModel() {
        mongo.remove(new Query(), AiAssistantCollections.EVAL_RUN);
        when(chatModel.chat(any(ChatRequest.class), any(ChatRequestOptions.class)))
                .thenAnswer(inv -> scripted.chat(inv.<ChatRequest>getArgument(0), inv.<ChatRequestOptions>getArgument(1)));
    }

    @AfterEach
    void cleanRuns() {
        mongo.remove(new Query(), AiAssistantCollections.EVAL_RUN);
    }

    static EvalCase evalCase(String id, String certNo, AccessLevel level, boolean refusal) {
        return new EvalCase(id, "g", level, certNo, "question of " + id,
                new EvalCase.Expect(List.of(), List.of(), refusal, List.of(), List.of(), null), List.of("rubric of " + id));
    }

    /** Inserts a RUNNING run started (and last beating) {@code age} ago and returns it as stored. */
    EvalRun insertRun(int totalCases, Duration age) {
        Instant started = clock.instant().minus(age);
        EvalRun draft = new EvalRun(null, new ConfigKey("instruction.contact-prep"), 2, 0L, started,
                List.of(new PromptVersion("system-prompt", 1)), "h".repeat(64), EvalStatus.RUNNING, null, "EEE22222",
                started, started, null, totalCases, 0, null, List.of(), 0L);
        return runs.findById(runs.insert(draft)).orElseThrow();
    }

    static AssistantConfig config(String key, int version, String content) {
        return new AssistantConfig(null, new ConfigKey(key), null, key, content, null, version, null, null, null, null, null, null,
                null, null);
    }

    static EvalRunCommandUseCase.EvalSnapshot snapshot() {
        return snapshot(List.of(), List.of());
    }

    static EvalRunCommandUseCase.EvalSnapshot snapshot(List<AssistantConfig> baselineInstructions,
                                                List<AssistantConfig> candidateInstructions) {
        return new EvalRunCommandUseCase.EvalSnapshot(
                new PublishedConfigs(config("system.main", 1, "BASELINE-PROMPT"), baselineInstructions),
                new PublishedConfigs(config("system.main", 2, "CANDIDATE-PROMPT"), candidateInstructions));
    }

    /** Scripts one chat answer (no sources, no digits). */
    void answerStep() {
        scripted.reply(answer(ANSWER_TOKENS, "這是回答"));
    }

    /** Scripts one judge reply: not refused, the single rubric item met. */
    void judgeStep() {
        scripted.reply(text("{\"refused\":false,\"rubric\":[{\"index\":1,\"met\":true}],\"comment\":\"評語內容\"}",
                JUDGE_TOKENS));
    }

    /** Scripts one routing reply that picks no instruction. */
    void routeStep() {
        scripted.reply(text("{\"category\":\"none\"}", 10));
    }

    /** answer, judge, answer, judge: one case with both sides answered. */
    void scriptFullCase() {
        answerStep();
        judgeStep();
        answerStep();
        judgeStep();
    }
}
