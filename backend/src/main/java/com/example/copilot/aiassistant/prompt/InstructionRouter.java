package com.example.copilot.aiassistant.prompt;

import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.common.RequestIdFilter;
import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.llm.LlmErrorMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Picks the one published instruction that fits a question with a single model call (routing spec 2.1). Never
 * throws: any model or parsing failure becomes {@link RouteSource#FALLBACK}.
 */
@Component
public class InstructionRouter {

    private static final Logger log = LoggerFactory.getLogger(InstructionRouter.class);
    private static final String SCHEMA_NAME = "route";
    private static final String NONE = "none";
    private static final String SCOPE_HEADING = "## 適用問題";
    private static final int MAX_HISTORY = 4;
    private static final int MAX_HISTORY_CODE_POINTS = 500;

    private final ChatModel chatModel;
    private final LlmErrorMapper errorMapper;
    private final JsonMapper mapper;

    public InstructionRouter(ChatModel chatModel, LlmErrorMapper errorMapper, JsonMapper mapper) {
        this.chatModel = chatModel;
        this.errorMapper = errorMapper;
        this.mapper = mapper;
    }

    public RouteDecision route(String redactedQuestion, List<ChatMessage> redactedHistory,
                               List<AssistantConfig> instructions) {
        if (instructions.isEmpty()) {
            return new RouteDecision(null, RouteSource.ROUTER, false, 0, 0, 0);
        }
        List<AssistantConfig> sorted = instructions.stream()
                .sorted(Comparator.comparing(AssistantConfig::getConfigKey)).toList();
        try {
            ChatResponse response = chatModel.chat(request(redactedQuestion, redactedHistory, sorted));
            TokenUsage usage = response.tokenUsage();
            return parse(response.aiMessage(), sorted, count(usage == null ? null : usage.inputTokenCount()),
                    count(usage == null ? null : usage.outputTokenCount()),
                    count(usage == null ? null : usage.totalTokenCount()));
        } catch (RuntimeException e) {
            return failed(errorMapper.map(e), 0, 0, 0);
        }
    }

    private RouteDecision parse(AiMessage message, List<AssistantConfig> instructions, long in, long out,
                                long total) {
        try {
            JsonNode category = mapper.readTree(message.text()).get("category");
            if (category != null && category.isString()) {
                String value = category.asString();
                if (NONE.equals(value)) {
                    return new RouteDecision(null, RouteSource.ROUTER, true, in, out, total);
                }
                if (instructions.stream().anyMatch(i -> i.getConfigKey().equals(value))) {
                    return new RouteDecision(value, RouteSource.ROUTER, true, in, out, total);
                }
            }
        } catch (RuntimeException e) {
            // Not JSON (or no text at all): handled as a failed route below.
        }
        return failed(ErrorCode.AI_ANSWER_FORMAT_INVALID, in, out, total);
    }

    private RouteDecision failed(ErrorCode code, long in, long out, long total) {
        // Code only: neither the question nor the model output may reach the log.
        log.warn("chat route failed requestId={} code={}", RequestIdFilter.current(), code);
        return new RouteDecision(null, RouteSource.FALLBACK, true, in, out, total);
    }

    private ChatRequest request(String question, List<ChatMessage> history, List<AssistantConfig> instructions) {
        ObjectNode user = mapper.createObjectNode();
        user.put("question", question);
        ArrayNode historyNode = user.putArray("history");
        List<ChatMessage> recent = history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size());
        for (ChatMessage message : recent) {
            switch (message) {
                case UserMessage m -> historyNode.add(turn("user", m.singleText()));
                case AiMessage m -> historyNode.add(turn("assistant", m.text()));
                default -> { }
            }
        }
        ArrayNode categories = user.putArray("categories");
        for (AssistantConfig instruction : instructions) {
            ObjectNode category = categories.addObject();
            category.put("key", instruction.getConfigKey());
            category.put("name", instruction.getName());
            category.put("scope", scope(instruction));
        }
        return ChatRequest.builder()
                .messages(SystemMessage.from(RouterPrompt.TEXT), UserMessage.from(mapper.writeValueAsString(user)))
                .responseFormat(responseFormat(instructions))
                .build();
    }

    private ObjectNode turn(String role, String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", role);
        String content = text == null ? "" : text;
        if (content.codePointCount(0, content.length()) > MAX_HISTORY_CODE_POINTS) {
            content = content.substring(0, content.offsetByCodePoints(0, MAX_HISTORY_CODE_POINTS));
        }
        node.put("content", content);
        return node;
    }

    /** The body of the "## 適用問題" section, or the instruction name when it is missing or empty. */
    private static String scope(AssistantConfig instruction) {
        String content = instruction.getContent();
        if (content != null) {
            boolean inScope = false;
            List<String> lines = new ArrayList<>();
            for (String line : content.split("\\R", -1)) {
                if (inScope && line.startsWith("## ")) {
                    break;
                }
                if (inScope) {
                    lines.add(line);
                } else if (line.strip().equals(SCOPE_HEADING)) {
                    inScope = true;
                }
            }
            String scope = String.join("\n", lines).strip();
            if (!scope.isEmpty()) {
                return scope;
            }
        }
        return instruction.getName();
    }

    private static long count(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }

    private static ResponseFormat responseFormat(List<AssistantConfig> instructions) {
        List<String> values = new ArrayList<>(instructions.stream().map(AssistantConfig::getConfigKey).toList());
        values.add(NONE);
        JsonObjectSchema root = JsonObjectSchema.builder()
                .addProperty("category", JsonEnumSchema.builder().enumValues(values).build())
                .required("category")
                .additionalProperties(false)
                .build();
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder().name(SCHEMA_NAME).rootElement(root).build())
                .build();
    }
}
