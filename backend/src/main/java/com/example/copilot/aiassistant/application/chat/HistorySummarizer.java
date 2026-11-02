package com.example.copilot.aiassistant.application.chat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.prompt.SummaryPrompt;
import com.example.copilot.aiassistant.domain.prompt.SummaryResult;
import com.example.copilot.aiassistant.domain.prompt.SummaryStatus;
import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Folds history that no longer fits into a short summary with a single model call (history summary spec 3). Never
 * throws: any model or parsing failure becomes {@link SummaryStatus#FAILED}.
 */
@Component
public class HistorySummarizer {

    private static final Logger log = LoggerFactory.getLogger(HistorySummarizer.class);
    private static final String SCHEMA_NAME = "summary";
    private static final int MAX_MESSAGE_CODE_POINTS = 1000;
    private static final int MAX_INPUT_CODE_POINTS = 6000;

    private final ChatModel chatModel;
    private final LlmErrorMapper errorMapper;
    private final JsonMapper mapper;
    private final PiiRedactor redactor;
    private final int summaryMaxChars;

    public HistorySummarizer(ChatModel chatModel, LlmErrorMapper errorMapper, JsonMapper mapper,
                             PiiRedactor redactor, AiAssistantProperties props) {
        this.chatModel = chatModel;
        this.errorMapper = errorMapper;
        this.mapper = mapper;
        this.redactor = redactor;
        this.summaryMaxChars = props.limits().summaryMaxChars();
    }

    /** {@code overflow}: already de-identified user and assistant messages, oldest first. */
    public SummaryResult summarize(String previousSummary, List<ChatMessage> overflow, CustomerContext customer) {
        int count = overflow.size();
        try {
            ChatResponse response = chatModel.chat(request(previousSummary, overflow));
            TokenUsage usage = response.tokenUsage();
            return parse(response.aiMessage(), customer, count,
                    count(usage == null ? null : usage.inputTokenCount()),
                    count(usage == null ? null : usage.outputTokenCount()),
                    count(usage == null ? null : usage.totalTokenCount()));
        } catch (RuntimeException e) {
            return failed(errorMapper.map(e), count, 0, 0, 0);
        }
    }

    private SummaryResult parse(AiMessage message, CustomerContext customer, int count, long in, long out,
                                long total) {
        try {
            JsonNode node = mapper.readTree(message.text()).get("summary");
            if (node != null && node.isString() && !node.asString().isBlank()) {
                String text = redactor.redact(node.asString(), customer)
                        .replace(PiiRedactor.SELF, CustomerDeidentifier.SUBJECT);
                return new SummaryResult(SummaryStatus.SUMMARIZED, truncate(text, summaryMaxChars), count, in, out,
                        total);
            }
        } catch (RuntimeException e) {
            // Not JSON (or no text at all): handled as a failed summary below.
        }
        return failed(ErrorCode.AI_ANSWER_FORMAT_INVALID, count, in, out, total);
    }

    private SummaryResult failed(ErrorCode code, int count, long in, long out, long total) {
        // Code only: neither the messages nor the model output may reach the log.
        log.warn("chat summary failed requestId={} code={}", RequestContext.currentRequestId(), code);
        return new SummaryResult(SummaryStatus.FAILED, null, count, in, out, total);
    }

    private ChatRequest request(String previousSummary, List<ChatMessage> overflow) {
        ObjectNode user = mapper.createObjectNode();
        if (previousSummary == null) {
            user.putNull("previousSummary");
        } else {
            user.put("previousSummary", previousSummary);
        }
        ArrayNode messages = user.putArray("messages");
        keepNewest(overflow).forEach(messages::add);
        return ChatRequest.builder()
                .messages(SystemMessage.from(SummaryPrompt.TEXT), UserMessage.from(mapper.writeValueAsString(user)))
                .responseFormat(responseFormat())
                .build();
    }

    /** Newest first until the total would pass the cap; older messages that do not fit are dropped. Oldest first. */
    private List<ObjectNode> keepNewest(List<ChatMessage> overflow) {
        List<ObjectNode> kept = new ArrayList<>();
        int used = 0;
        for (int i = overflow.size() - 1; i >= 0; i--) {
            ObjectNode turn = turn(overflow.get(i));
            if (turn == null) {
                continue;
            }
            String content = turn.get("content").asString();
            int size = content.codePointCount(0, content.length());
            if (used + size > MAX_INPUT_CODE_POINTS) {
                break;
            }
            used += size;
            kept.add(turn);
        }
        Collections.reverse(kept);
        return kept;
    }

    private ObjectNode turn(ChatMessage message) {
        String role;
        String text;
        switch (message) {
            case UserMessage m -> {
                role = "user";
                text = m.singleText();
            }
            case AiMessage m -> {
                role = "assistant";
                text = m.text();
            }
            default -> {
                return null;
            }
        }
        ObjectNode node = mapper.createObjectNode();
        node.put("role", role);
        node.put("content", truncate(text == null ? "" : text, MAX_MESSAGE_CODE_POINTS));
        return node;
    }

    private static String truncate(String text, int maxCodePoints) {
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    private static long count(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }

    private static ResponseFormat responseFormat() {
        JsonObjectSchema root = JsonObjectSchema.builder()
                .addProperty("summary", JsonStringSchema.builder().build())
                .required("summary")
                .additionalProperties(false)
                .build();
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder().name(SCHEMA_NAME).rootElement(root).build())
                .build();
    }
}
