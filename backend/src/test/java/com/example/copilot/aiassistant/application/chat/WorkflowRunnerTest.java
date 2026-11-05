package com.example.copilot.aiassistant.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.chat.WorkflowRunner.Prepared;
import com.example.copilot.aiassistant.application.chat.WorkflowRunner.StepExecution;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.prompt.WorkflowStep;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.CitationIds;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolExecutor;
import com.example.copilot.aiassistant.domain.tool.ToolGuard;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import com.example.copilot.aiassistant.domain.tool.ToolStatus;
import com.example.copilot.aiassistant.domain.tool.customer.GetCustomerTool;
import com.example.copilot.aiassistant.domain.tool.customer.GetRealTimeCustomerTool;
import com.example.copilot.aiassistant.domain.tool.market.GetCompanyAnnouncementsTool;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool;
import com.example.copilot.aiassistant.domain.tool.testsupport.FakeTool;
import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.testsupport.LogCapture;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WorkflowRunnerTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final AiAssistantProperties PROPS = new AiAssistantProperties(
            new AiAssistantProperties.Llm("openai", "test-key", null, "test-model", null, "none",
                    null, null, Duration.ofSeconds(30), 1500),
            new AiAssistantProperties.Limits(1000, 10, 8000, 5, 3, Duration.ofSeconds(60), 16000,
                    Duration.ofSeconds(5), 4096, 10, 1000), null, null, null, null);
    private static final String STOCK_REQUEST = "請提供要查詢的股票代號（例如 2330）。";
    private static final String DROPPED_NOTICE = "未能完成「未知工具」，以下回答可能不完整。";
    private static final ToolContext CONTEXT = new ToolContext(
            new UserContext("ICRM00028", "理專", "FC", Set.of("AI_ASSISTANT_USE")),
            new CustomerContext(TestCustomers.RETIREE, AccessLevel.FULL, Set.of(), Set.of()));

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PiiRedactor redactor = new PiiRedactor();
    private final ScriptedChatModel model = new ScriptedChatModel();

    record Query(@NotBlank String query) {
    }

    record NoArgs() {
    }

    record Combined(List<String> sections, @NotBlank String query) {
    }

    /** A tool with one free-text required input and no {@code inputRequest()}. */
    private static final class QueryTool extends FakeTool<Query> {

        QueryTool(String name) {
            super(name, Query.class, AccessLevel.LIMITED, Duration.ofSeconds(3),
                    (args, context) -> ToolResult.success(Map.of("query", args.query()), List.of()));
        }

        @Override
        public ToolSpecification specification() {
            return ToolSpecification.builder().name(name()).description("test")
                    .parameters(JsonObjectSchema.builder().addStringProperty("query").required("query")
                            .additionalProperties(false).build())
                    .build();
        }
    }

    private WorkflowRunner runner(AssistantTool<?>... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools));
        ToolGuard guard = new ToolGuard(registry, VALIDATOR, mapper);
        return new WorkflowRunner(registry, new ToolExecutor(guard, redactor, mapper, Duration.ofSeconds(5), 4096),
                guard, new StepArgumentExtractor(model, new LlmErrorMapper(), mapper, PROPS), mapper);
    }

    private Prepared prepare(WorkflowRunner runner, Set<String> offered, WorkflowStep... steps) {
        return runner.prepare(List.of(steps), "問題", List.of(), null, offered);
    }

    private static WorkflowStep step(String tool, String... options) {
        return new WorkflowStep(tool, List.of(options));
    }

    private static ToolExecutionRequest request(String id, String name) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments("{}").build();
    }

    @Test
    void fixedOptionsBecomeArguments() {
        Prepared prepared = prepare(runner(new GetCustomerTool(redactor)), Set.of("getCustomer"),
                step("getCustomer", "management", "behavior"));

        assertThat(prepared.requests()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo("workflow-1");
            assertThat(r.name()).isEqualTo("getCustomer");
            assertThat(r.arguments()).isEqualTo("{\"sections\":[\"management\",\"behavior\"]}");
        });
        assertThat(prepared.inputRequests()).isEmpty();
        assertThat(prepared.extraction().modelCalled()).isFalse();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void noOptionsBecomeEmptyArguments() {
        Prepared prepared = prepare(runner(new GetRealTimeCustomerTool(redactor)), Set.of("getRealTimeCustomer"),
                step("getRealTimeCustomer"));

        assertThat(prepared.requests()).singleElement()
                .satisfies(r -> assertThat(r.arguments()).isEqualTo("{}"));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void extractedValueBecomesArguments() {
        model.reply(ScriptedChatModel.text("{\"step1\":{\"stockCode\":\"2330\"}}", 40));

        Prepared prepared = prepare(runner(new GetStockQuoteTool(null)), Set.of("getStockQuote"),
                step("getStockQuote"));

        assertThat(prepared.inputRequests()).isEmpty();
        assertThat(prepared.requests()).singleElement()
                .satisfies(r -> assertThat(r.arguments()).isEqualTo("{\"stockCode\":\"2330\"}"));
        assertThat(prepared.extraction().totalTokens()).isEqualTo(40);
    }

    @Test
    void optionsAndExtractedInputsAreCombined() {
        FakeTool<Combined> tool = new FakeTool<>("combined", Combined.class, AccessLevel.LIMITED,
                Duration.ofSeconds(3), (args, context) -> ToolResult.success(Map.of("a", "b"), List.of())) {
            @Override
            public String optionsArgument() {
                return "sections";
            }

            @Override
            public ToolSpecification specification() {
                return ToolSpecification.builder().name(name()).description("test")
                        .parameters(JsonObjectSchema.builder().addStringProperty("query")
                                .required("sections", "query").additionalProperties(false).build())
                        .build();
            }
        };
        model.reply(ScriptedChatModel.text("{\"step1\":{\"query\":\"q\"}}", 40));

        Prepared prepared = prepare(runner(tool), Set.of("combined"), step("combined", "a", "b"));

        assertThat(prepared.inputRequests()).isEmpty();
        assertThat(prepared.requests()).singleElement()
                .satisfies(r -> assertThat(r.arguments()).isEqualTo("{\"sections\":[\"a\",\"b\"],\"query\":\"q\"}"));
    }

    @Test
    void missingInputAsksForIt() {
        model.reply(ScriptedChatModel.text("{\"step1\":{\"stockCode\":\"\"}}", 40));

        Prepared prepared = prepare(runner(new GetStockQuoteTool(null)), Set.of("getStockQuote"),
                step("getStockQuote"));

        assertThat(prepared.inputRequests()).containsExactly(STOCK_REQUEST);
        assertThat(prepared.requests()).isEmpty();
    }

    @Test
    void nonCodeValueAsksForIt() {
        model.reply(ScriptedChatModel.text("{\"step1\":{\"stockCode\":\"台積電\"}}", 40));

        Prepared prepared = prepare(runner(new GetStockQuoteTool(null)), Set.of("getStockQuote"),
                step("getStockQuote"));

        assertThat(prepared.inputRequests()).containsExactly(STOCK_REQUEST);
        assertThat(prepared.requests()).isEmpty();
    }

    @Test
    void sameRequestIsNotRepeated() {
        model.reply(ScriptedChatModel.text("{\"step1\":{\"stockCode\":\"\"},\"step2\":{\"stockCode\":\"\"}}", 40));

        Prepared prepared = prepare(runner(new GetStockQuoteTool(null), new GetCompanyAnnouncementsTool(null)),
                Set.of("getStockQuote", "getCompanyAnnouncements"),
                step("getStockQuote"), step("getCompanyAnnouncements"));

        assertThat(prepared.inputRequests()).containsExactly(STOCK_REQUEST);
    }

    @Test
    void toolWithoutInputRequestUsesTheGenericText() {
        model.reply(ScriptedChatModel.text("{\"step1\":{\"query\":\"\"}}", 40));

        Prepared prepared = prepare(runner(new QueryTool("needsQuery")), Set.of("needsQuery"), step("needsQuery"));

        assertThat(prepared.inputRequests()).containsExactly("請提供查詢所需的資料。");
    }

    @Test
    void extractionFailureDoesNotAsk() {
        model.fail(new IllegalStateException("provider down"));

        Prepared prepared = prepare(runner(new GetStockQuoteTool(null), new GetRealTimeCustomerTool(redactor)),
                Set.of("getStockQuote", "getRealTimeCustomer"), step("getStockQuote"), step("getRealTimeCustomer"));

        assertThat(prepared.inputRequests()).isEmpty();
        assertThat(prepared.extraction().failed()).isTrue();
        assertThat(prepared.requests()).extracting(ToolExecutionRequest::name, ToolExecutionRequest::arguments)
                .containsExactly(tuple("getStockQuote", "{}"),
                        tuple("getRealTimeCustomer", "{}"));
    }

    @Test
    void notOfferedToolIsNotExtracted() {
        Prepared prepared = prepare(runner(new GetStockQuoteTool(null)), Set.of(), step("getStockQuote"));

        assertThat(model.requests()).isEmpty();
        assertThat(prepared.extraction().modelCalled()).isFalse();
        assertThat(prepared.inputRequests()).isEmpty();
        assertThat(prepared.requests()).singleElement()
                .satisfies(r -> assertThat(r.arguments()).isEqualTo("{}"));
    }

    private static FakeTool<NoArgs> plain(String name) {
        return new FakeTool<>(name, NoArgs.class, AccessLevel.LIMITED, Duration.ofSeconds(3),
                (args, context) -> ToolResult.success(Map.of("a", "b"), List.of()));
    }

    @Test
    void unregisteredStepsAreDroppedAndReported() {
        WorkflowRunner runner = runner(plain("toolOne"), plain("toolTwo"));
        Set<String> offered = Set.of("toolOne", "toolTwo");

        try (LogCapture capture = LogCapture.of(WorkflowRunner.class, Level.WARN)) {
            Prepared malformedFirst = prepare(runner, offered, step("查詢"), step("toolOne"), step("toolTwo"));
            Prepared removedBetween = prepare(runner, offered, step("toolOne"), step("removedTool"), step("toolTwo"));

            for (Prepared prepared : List.of(malformedFirst, removedBetween)) {
                assertThat(prepared.requests()).extracting(ToolExecutionRequest::id, ToolExecutionRequest::name)
                        .containsExactly(tuple("workflow-1", "toolOne"), tuple("workflow-2", "toolTwo"));
                assertThat(prepared.droppedNotices()).containsExactly(DROPPED_NOTICE);
                assertThat(prepared.inputRequests()).isEmpty();
            }
            List<String> lines = capture.events().stream().map(LogCapture::render).toList();
            assertThat(lines).hasSize(2).allSatisfy(line -> assertThat(line)
                    .contains("workflow step dropped", "reason=TOOL_NOT_REGISTERED")
                    .doesNotContain("查詢", "removedTool"));
        }
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void everyDroppedStepIsReported() {
        Prepared prepared = prepare(runner(plain("toolOne")), Set.of("toolOne"), step("查詢"), step("removedTool"),
                step("toolOne"));

        assertThat(prepared.requests()).extracting(ToolExecutionRequest::name).containsExactly("toolOne");
        assertThat(prepared.droppedNotices()).containsExactly(DROPPED_NOTICE, DROPPED_NOTICE);
    }

    @Test
    void droppedStepsCountTowardTheCut() {
        Prepared prepared = prepare(runner(plain("toolOne"), plain("toolTwo"), plain("toolThree")),
                Set.of("toolOne", "toolTwo", "toolThree"), step("查詢"), step("toolOne"), step("toolTwo"),
                step("toolThree"));

        assertThat(prepared.requests()).extracting(ToolExecutionRequest::name).containsExactly("toolOne", "toolTwo");
        assertThat(prepared.droppedNotices()).containsExactly(DROPPED_NOTICE);
    }

    @Test
    void extraMarkersAreCut() {
        try (LogCapture capture = LogCapture.of(WorkflowRunner.class, Level.WARN)) {
            Prepared prepared = prepare(runner(plain("toolOne"), plain("toolTwo"), plain("toolThree"),
                            plain("toolFour")), Set.of("toolOne", "toolTwo", "toolThree", "toolFour"),
                    step("toolOne"), step("toolTwo"), step("toolThree"), step("toolFour"));

            assertThat(prepared.requests()).extracting(ToolExecutionRequest::id, ToolExecutionRequest::name)
                    .containsExactly(tuple("workflow-1", "toolOne"),
                            tuple("workflow-2", "toolTwo"),
                            tuple("workflow-3", "toolThree"));
            List<String> lines = capture.events().stream().map(LogCapture::render).toList();
            assertThat(lines).singleElement().satisfies(line -> assertThat(line)
                    .contains("steps=4", "kept=3").doesNotContain("toolOne", "toolFour"));
        }
    }

    @Test
    void stepsRunInOrderAndFailuresDoNotStopThem() {
        List<String> ran = new ArrayList<>();
        WorkflowRunner runner = runner(
                new FakeTool<>("first", NoArgs.class, AccessLevel.LIMITED, Duration.ofSeconds(3), (args, context) -> {
                    ran.add("first");
                    return ToolResult.failure(ToolStatus.ERROR, "X", "查詢失敗");
                }),
                new FakeTool<>("second", NoArgs.class, AccessLevel.LIMITED, Duration.ofSeconds(3), (args, context) -> {
                    ran.add("second");
                    return ToolResult.success(Map.of("a", "b"), List.of());
                }));
        AtomicInteger checks = new AtomicInteger();
        CitationIds ids = new CitationIds();

        List<StepExecution> executions = runner.execute(
                List.of(request("workflow-1", "first"), request("workflow-2", "second")), CONTEXT,
                Set.of("first", "second"), ids, checks::incrementAndGet);

        assertThat(ran).containsExactly("first", "second");
        assertThat(checks).hasValue(2);
        assertThat(executions).extracting(e -> e.outcome().result().status())
                .containsExactly(ToolStatus.ERROR, ToolStatus.SUCCESS);
        assertThat(executions.get(1).outcome().citationId()).isEqualTo("S1");
        assertThat(runner.failureNotices(executions)).containsExactly("未能完成「測試 first」，以下回答可能不完整。");
    }

    @Test
    void failureNoticeUsesTheRegisteredPurpose() {
        WorkflowRunner runner = runner(new GetRealTimeCustomerTool(redactor));

        List<StepExecution> executions = runner.execute(List.of(request("workflow-1", "getRealTimeCustomer")), CONTEXT,
                Set.of(), new CitationIds(), () -> { });

        assertThat(executions.getFirst().outcome().purpose()).isEqualTo("未知工具");
        assertThat(runner.failureNotices(executions)).containsExactly("未能完成「查詢顧客即時註記與提醒」，以下回答可能不完整。");
    }
}
