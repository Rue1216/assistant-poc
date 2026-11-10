package com.example.copilot.aiassistant.application.chat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.llm.LlmCallPurpose;
import com.example.copilot.aiassistant.application.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.application.llm.LlmRequestParameters;
import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.prompt.StepArgumentPrompt;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Finds the required inputs of workflow steps in the question and recent conversation with a single light model
 * call. Never throws: any model or parsing failure is reported through {@link Result#failed()}.
 */
@Component
@Slf4j
public class StepArgumentExtractor {

    private static final String SCHEMA_NAME = "step_arguments";

    /**
     * @param argumentsByTool tool name to the JSON arguments object holding its required inputs; empty values
     *                        are passed through for the tool guard to judge
     * @param failed          the call or its reply failed; no arguments are returned
     * @param modelCalled     false when no tool needed any input, so the model was not called
     */
    public record Result(Map<String, String> argumentsByTool, boolean failed, boolean modelCalled,
                         long inputTokens, long outputTokens, long totalTokens) {
    }

    private final ChatModel chatModel;
    private final LlmErrorMapper errorMapper;
    private final JsonMapper mapper;
    private final int summaryMaxChars;
    private final String reasoningEffort;

    public StepArgumentExtractor(ChatModel chatModel, LlmErrorMapper errorMapper, JsonMapper mapper,
                                 AiAssistantProperties props) {
        this.chatModel = chatModel;
        this.errorMapper = errorMapper;
        this.mapper = mapper;
        this.summaryMaxChars = props.limits().summaryMaxChars();
        this.reasoningEffort = props.llm().routerReasoningEffort();
    }

    /**
     * @param summary the de-identified summary of earlier turns, or null when there is none
     * @param tools   the workflow's tools in step order; those without required inputs are left out
     */
    public Result extract(String redactedQuestion, List<ChatMessage> redactedHistory, String summary,
                          List<AssistantTool<?>> tools) {
        List<AssistantTool<?>> needy = tools.stream().filter(t -> !t.requiredInputs().isEmpty()).toList();
        if (needy.isEmpty()) {
            return new Result(Map.of(), false, false, 0, 0, 0);
        }
        try {
            ChatResponse response = chatModel.chat(
                    request(redactedQuestion, redactedHistory, summary, needy), LlmCallPurpose.STEP_ARGUMENTS.options());
            TokenUsage usage = response.tokenUsage();
            return parse(response.aiMessage().text(), needy, count(usage == null ? null : usage.inputTokenCount()),
                    count(usage == null ? null : usage.outputTokenCount()),
                    count(usage == null ? null : usage.totalTokenCount()));
        } catch (RuntimeException e) {
            return failed(errorMapper.map(e), 0, 0, 0);
        }
    }

    private Result parse(String text, List<AssistantTool<?>> tools, long in, long out, long total) {
        try {
            JsonNode root = mapper.readTree(text);
            Map<String, String> arguments = new LinkedHashMap<>();
            for (int i = 0; i < tools.size(); i++) {
                JsonNode step = root.get(stepKey(i));
                if (step == null || !step.isObject()) {
                    return failed(ErrorCode.AI_ANSWER_FORMAT_INVALID, in, out, total);
                }
                ObjectNode picked = mapper.createObjectNode();
                for (String input : tools.get(i).requiredInputs()) {
                    JsonNode value = step.get(input);
                    picked.put(input, value != null && value.isString() ? value.asString() : "");
                }
                arguments.put(tools.get(i).name(), mapper.writeValueAsString(picked));
            }
            return new Result(arguments, false, true, in, out, total);
        } catch (RuntimeException e) {
            // Not JSON (or no text at all): handled as a failed extraction below.
            return failed(ErrorCode.AI_ANSWER_FORMAT_INVALID, in, out, total);
        }
    }

    private Result failed(ErrorCode code, long in, long out, long total) {
        // Code only: neither the question nor the model output may reach the log.
        log.warn("step arguments failed traceId={} code={}", RequestContext.currentTraceId(), code);
        return new Result(Map.of(), true, true, in, out, total);
    }

    private ChatRequest request(String question, List<ChatMessage> history, String summary,
                                List<AssistantTool<?>> tools) {
        ObjectNode user = RoutingInput.of(mapper, question, history, summary, summaryMaxChars);
        ArrayNode steps = user.putArray("steps");
        for (int i = 0; i < tools.size(); i++) {
            ObjectNode step = steps.addObject();
            step.put("key", stepKey(i));
            step.put("tool", tools.get(i).name());
            step.put("description", tools.get(i).specification().description());
        }
        return ChatRequest.builder()
                .messages(SystemMessage.from(StepArgumentPrompt.TEXT),
                        UserMessage.from(mapper.writeValueAsString(user)))
                .parameters(LlmRequestParameters.of(responseFormat(tools), reasoningEffort))
                .build();
    }

    private static String stepKey(int index) {
        return "step" + (index + 1);
    }

    private static long count(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }

    /** One strict object per step, holding only that tool's required inputs. */
    private static ResponseFormat responseFormat(List<AssistantTool<?>> tools) {
        JsonObjectSchema.Builder root = JsonObjectSchema.builder();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            AssistantTool<?> tool = tools.get(i);
            var properties = tool.specification().parameters().properties();
            JsonObjectSchema.Builder step = JsonObjectSchema.builder();
            for (String input : tool.requiredInputs()) {
                step.addProperty(input, properties.get(input));
            }
            root.addProperty(stepKey(i), step.required(tool.requiredInputs()).additionalProperties(false).build());
            keys.add(stepKey(i));
        }
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name(SCHEMA_NAME)
                        .rootElement(root.required(keys).additionalProperties(false).build())
                        .build())
                .build();
    }
}
