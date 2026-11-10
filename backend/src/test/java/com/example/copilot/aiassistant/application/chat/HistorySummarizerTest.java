package com.example.copilot.aiassistant.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.llm.LlmCallPurpose;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.prompt.SummaryPrompt;
import com.example.copilot.aiassistant.domain.prompt.SummaryResult;
import com.example.copilot.aiassistant.domain.prompt.SummaryStatus;
import com.example.copilot.testsupport.LogCapture;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HistorySummarizerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AiAssistantProperties props = new AiAssistantProperties(null,
            new AiAssistantProperties.Limits(1000, 10, 8000, 5, 3, Duration.ofSeconds(60), 16000,
                    Duration.ofSeconds(5), 4096, 10, 1000), null, null, null, null);
    private static final CustomerContext CUSTOMER = new CustomerContext(TestCustomers.RETIREE, AccessLevel.FULL,
            Set.of(TestCustomers.RETIREE, "王小明"),
            Set.of("0912-000-111", "xiaoming.wang@example.com", "0123456789012"));

    private HistorySummarizer summarizer(ScriptedChatModel model) {
        return new HistorySummarizer(model, new LlmErrorMapper(), mapper, new PiiRedactor(), props);
    }

    private static List<ChatMessage> overflow() {
        return List.of(UserMessage.from("【本顧客】月配息多少？"), AiMessage.from("約 3 萬"),
                UserMessage.from("那風險呢？"));
    }

    private static String summaryJson(String summary) {
        return "{\"summary\":\"" + summary + "\"}";
    }

    @Test
    void requestShape() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(summaryJson("- x"), 5));

        summarizer(model).summarize("舊摘要", overflow(), CUSTOMER);

        ChatRequest request = model.requests().getFirst();
        assertThat(request.messages()).hasSize(2);
        assertThat(((SystemMessage) request.messages().get(0)).text()).isEqualTo(SummaryPrompt.TEXT);
        assertThat(request.toolSpecifications()).isNullOrEmpty();
        JsonNode user = mapper.readTree(((UserMessage) request.messages().get(1)).singleText());
        assertThat(user.get("previousSummary").asString()).isEqualTo("舊摘要");
        JsonNode messages = user.get("messages");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).get("role").asString()).isEqualTo("user");
        assertThat(messages.get(0).get("content").asString()).isEqualTo("【本顧客】月配息多少？");
        assertThat(messages.get(1).get("role").asString()).isEqualTo("assistant");
        assertThat(messages.get(1).get("content").asString()).isEqualTo("約 3 萬");
        assertThat(messages.get(2).get("role").asString()).isEqualTo("user");

        assertThat(request.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(request.responseFormat().jsonSchema().name()).isEqualTo("summary");
        JsonObjectSchema root = (JsonObjectSchema) request.responseFormat().jsonSchema().rootElement();
        assertThat(root.required()).containsExactly("summary");
        assertThat(root.additionalProperties()).isFalse();
        assertThat(root.properties()).containsOnlyKeys("summary");
        assertThat(root.properties().get("summary")).isInstanceOf(JsonStringSchema.class);
    }

    @Test
    void nullPreviousSummaryIsJsonNull() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(summaryJson("- x"), 5));

        summarizer(model).summarize(null, overflow(), CUSTOMER);

        JsonNode user = mapper.readTree(((UserMessage) model.requests().getFirst().messages().get(1)).singleText());
        assertThat(user.has("previousSummary")).isTrue();
        assertThat(user.get("previousSummary").isNull()).isTrue();
    }

    @Test
    void summarizes() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.usage(
                ScriptedChatModel.text(summaryJson("- 本顧客關心月配息"), 0), 40, 10));

        SummaryResult result = summarizer(model).summarize(null, overflow(), CUSTOMER);

        assertThat(result).isEqualTo(new SummaryResult(SummaryStatus.SUMMARIZED, "- 本顧客關心月配息", 3, 40, 10, 50));
    }

    @Test
    void summaryCallCarriesSummaryPurpose() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(summaryJson("- x"), 5));

        summarizer(model).summarize(null, overflow(), CUSTOMER);

        assertThat(model.purposes()).containsExactly(LlmCallPurpose.SUMMARY);
    }

    @Test
    void redactsOutput() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(
                summaryJson("- 王小明來電，手機 0912-000-111"), 5));

        SummaryResult result = summarizer(model).summarize(null, overflow(), CUSTOMER);

        assertThat(result.status()).isEqualTo(SummaryStatus.SUMMARIZED);
        assertThat(result.text()).contains("本顧客", "【已遮蔽】").doesNotContain("王小明", "【本顧客】", "0912-000-111");
    }

    @Test
    void truncatesToSummaryMaxChars() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(
                summaryJson("字".repeat(1500)), 5));

        SummaryResult result = summarizer(model).summarize(null, overflow(), CUSTOMER);

        assertThat(result.text().codePointCount(0, result.text().length())).isEqualTo(1000);
    }

    @Test
    void capsEachMessageAndTotalInput() {
        List<ChatMessage> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String text = (char) ('a' + i) + "字".repeat(1999);
            many.add(i % 2 == 0 ? UserMessage.from(text) : AiMessage.from(text));
        }
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.text(summaryJson("- x"), 5));

        SummaryResult result = summarizer(model).summarize(null, many, CUSTOMER);

        JsonNode user = mapper.readTree(((UserMessage) model.requests().getFirst().messages().get(1)).singleText());
        JsonNode messages = user.get("messages");
        int total = 0;
        for (JsonNode m : messages) {
            String content = m.get("content").asString();
            int size = content.codePointCount(0, content.length());
            assertThat(size).isLessThanOrEqualTo(1000);
            total += size;
        }
        assertThat(total).isLessThanOrEqualTo(6000);
        assertThat(messages).hasSize(6);
        // The newest six, oldest first: c..h
        assertThat(messages.get(0).get("content").asString()).startsWith("c");
        assertThat(messages.get(5).get("content").asString()).startsWith("h");
        assertThat(result.summarizedCount()).isEqualTo(8);
    }

    @Test
    void failsOnNonJson() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.usage(
                ScriptedChatModel.text("不是 JSON", 0), 40, 10));

        assertThat(summarizer(model).summarize(null, overflow(), CUSTOMER))
                .isEqualTo(new SummaryResult(SummaryStatus.FAILED, null, 3, 40, 10, 50));
    }

    @Test
    void failsOnBlankSummary() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.usage(
                ScriptedChatModel.text(summaryJson("  "), 0), 40, 10));

        assertThat(summarizer(model).summarize(null, overflow(), CUSTOMER))
                .isEqualTo(new SummaryResult(SummaryStatus.FAILED, null, 3, 40, 10, 50));
    }

    @Test
    void failsOnModelError() {
        ScriptedChatModel model = new ScriptedChatModel().fail(new RateLimitException("x"));

        assertThat(summarizer(model).summarize(null, overflow(), CUSTOMER))
                .isEqualTo(new SummaryResult(SummaryStatus.FAILED, null, 3, 0, 0, 0));
    }

    @Test
    void failureLogsCodeOnly() {
        try (LogCapture capture = LogCapture.of(HistorySummarizer.class, Level.WARN)) {
            summarizer(new ScriptedChatModel().fail(new IllegalStateException("提問內容子丑寅")))
                    .summarize("摘要內容卯辰巳", List.of(UserMessage.from("提問內容子丑寅")), CUSTOMER);

            List<String> lines = capture.events().stream().map(LogCapture::render).toList();
            assertThat(lines).hasSize(1);
            assertThat(lines.getFirst()).contains("chat summary failed traceId=", "code=AI_LLM_UNAVAILABLE")
                    .doesNotContain("提問內容子丑寅", "摘要內容卯辰巳");
        }
    }
}
