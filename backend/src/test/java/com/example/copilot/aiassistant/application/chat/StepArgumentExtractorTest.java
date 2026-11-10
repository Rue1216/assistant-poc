package com.example.copilot.aiassistant.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.chat.StepArgumentExtractor.Result;
import com.example.copilot.aiassistant.application.llm.LlmCallPurpose;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.domain.prompt.StepArgumentPrompt;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.market.GetCompanyAnnouncementsTool;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool;
import com.example.copilot.testsupport.LogCapture;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StepArgumentExtractorTest {

    private static final String BOTH =
            "{\"step1\":{\"stockCode\":\"2330\"},\"step2\":{\"stockCode\":\"2330\"}}";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final GetStockQuoteTool quote = new GetStockQuoteTool(null);
    private final GetCompanyAnnouncementsTool announcements = new GetCompanyAnnouncementsTool(null);
    private final List<AssistantTool<?>> tools = List.of(quote, announcements);

    private StepArgumentExtractor extractor(ScriptedChatModel model) {
        AiAssistantProperties props = new AiAssistantProperties(
                new AiAssistantProperties.Llm("openai", "test-key", null, "test-model", null, "none",
                        null, null, Duration.ofSeconds(30), 1500),
                new AiAssistantProperties.Limits(1000, 10, 8000, 5, 3, Duration.ofSeconds(60), 16000,
                        Duration.ofSeconds(5), 4096, 10, 1000), null, null, null, null);
        return new StepArgumentExtractor(model, new LlmErrorMapper(), mapper, props);
    }

    @Test
    void extractsOneCallForAllTools() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(BOTH, 50));

        Result r = extractor(model).extract("2330 的行情和重訊", List.of(), null, tools);

        assertThat(r.argumentsByTool()).containsEntry("getStockQuote", "{\"stockCode\":\"2330\"}")
                .containsEntry("getCompanyAnnouncements", "{\"stockCode\":\"2330\"}");
        assertThat(r.failed()).isFalse();
        assertThat(r.modelCalled()).isTrue();
        assertThat(r.totalTokens()).isEqualTo(50);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void extractionCallCarriesStepArgumentsPurpose() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(BOTH, 50));

        extractor(model).extract("2330 的行情和重訊", List.of(), null, tools);

        assertThat(model.purposes()).containsExactly(LlmCallPurpose.STEP_ARGUMENTS);
    }

    @Test
    void requestHasNoToolsAndAStrictSchemaOfRequiredInputsOnly() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(BOTH, 50));

        extractor(model).extract("問題", List.of(), null, tools);

        ChatRequest request = model.requests().getFirst();
        assertThat(request.toolSpecifications()).isNullOrEmpty();
        assertThat(request.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(request.responseFormat().jsonSchema().name()).isEqualTo("step_arguments");
        JsonObjectSchema root = (JsonObjectSchema) request.responseFormat().jsonSchema().rootElement();
        assertThat(root.required()).containsExactly("step1", "step2");
        assertThat(root.additionalProperties()).isFalse();
        JsonObjectSchema step2 = (JsonObjectSchema) root.properties().get("step2");
        assertThat(step2.properties()).containsOnlyKeys("stockCode");
        assertThat(step2.required()).containsExactly("stockCode");
        assertThat(step2.additionalProperties()).isFalse();
    }

    @Test
    void systemTextIsTheFixedPromptOnly() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(BOTH, 50));

        extractor(model).extract("問題", List.of(), null, tools);

        ChatRequest request = model.requests().getFirst();
        assertThat(request.messages()).hasSize(2);
        assertThat(((SystemMessage) request.messages().get(0)).text()).isEqualTo(StepArgumentPrompt.TEXT);
    }

    @Test
    void sendsQuestionSummaryRecentHistoryAndSteps() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(BOTH, 50));
        List<ChatMessage> history = new ArrayList<>();
        history.add(UserMessage.from("第一則被捨棄"));
        history.add(AiMessage.from("a2"));
        history.add(UserMessage.from("u3"));
        history.add(AiMessage.from("a".repeat(499) + "😀額外"));
        history.add(UserMessage.from("u5"));

        extractor(model).extract("2330 的行情和重訊", history, "- 關心台積電", tools);

        JsonNode user = mapper.readTree(((UserMessage) model.requests().getFirst().messages().get(1)).singleText());
        assertThat(user.get("question").asString()).isEqualTo("2330 的行情和重訊");
        assertThat(user.get("summary").asString()).isEqualTo("- 關心台積電");
        JsonNode h = user.get("history");
        assertThat(h).hasSize(4);
        assertThat(h.get(0).get("content").asString()).isEqualTo("a2");
        String clipped = h.get(2).get("content").asString();
        assertThat(clipped.codePointCount(0, clipped.length())).isEqualTo(500);
        JsonNode steps = user.get("steps");
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).get("key").asString()).isEqualTo("step1");
        assertThat(steps.get(0).get("tool").asString()).isEqualTo("getStockQuote");
        assertThat(steps.get(0).get("description").asString()).isEqualTo(quote.specification().description());
        assertThat(steps.get(1).get("key").asString()).isEqualTo("step2");
        assertThat(steps.get(1).get("tool").asString()).isEqualTo("getCompanyAnnouncements");
    }

    @Test
    void noToolsMeansNoModelCall() {
        ScriptedChatModel model = new ScriptedChatModel();

        Result r = extractor(model).extract("問題", List.of(), null, List.of());

        assertThat(r.modelCalled()).isFalse();
        assertThat(r.failed()).isFalse();
        assertThat(r.argumentsByTool()).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void modelFailureIsReportedNotThrown() {
        try (LogCapture capture = LogCapture.of(StepArgumentExtractor.class, Level.WARN)) {
            Result r = extractor(new ScriptedChatModel().fail(new IllegalStateException("提問內容子丑寅")))
                    .extract("提問內容子丑寅", List.of(), null, tools);

            assertThat(r.failed()).isTrue();
            assertThat(r.modelCalled()).isTrue();
            assertThat(r.argumentsByTool()).isEmpty();
            List<String> lines = capture.events().stream().map(LogCapture::render).toList();
            assertThat(lines).hasSize(1);
            assertThat(lines.getFirst()).contains("step arguments failed requestId=", "code=AI_LLM_UNAVAILABLE")
                    .doesNotContain("提問內容子丑寅");
        }
    }

    @Test
    void unparsableReplyIsFailed() {
        Result notJson = extractor(new ScriptedChatModel().reply(ScriptedChatModel.text("不是 JSON", 7)))
                .extract("問題", List.of(), null, tools);
        assertThat(notJson.failed()).isTrue();
        assertThat(notJson.totalTokens()).isEqualTo(7);

        Result missing = extractor(new ScriptedChatModel()
                .reply(ScriptedChatModel.text("{\"step2\":{\"stockCode\":\"2330\"}}", 9)))
                .extract("問題", List.of(), null, tools);
        assertThat(missing.failed()).isTrue();
        assertThat(missing.argumentsByTool()).isEmpty();
        assertThat(missing.totalTokens()).isEqualTo(9);
    }

    @Test
    void emptyValueIsPassedThrough() {
        Result r = extractor(new ScriptedChatModel()
                .reply(ScriptedChatModel.text("{\"step1\":{\"stockCode\":\"\"}}", 5)))
                .extract("問題", List.of(), null, List.of(quote));

        assertThat(r.failed()).isFalse();
        assertThat(r.argumentsByTool()).containsEntry("getStockQuote", "{\"stockCode\":\"\"}");
    }
}
