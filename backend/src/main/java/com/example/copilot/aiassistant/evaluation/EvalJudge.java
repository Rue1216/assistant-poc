package com.example.copilot.aiassistant.evaluation;

import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Asks the model to judge one answer against its rubric (prompt-evaluation spec 4.5). Never throws: any model or
 * parsing failure becomes {@link JudgeVerdict#failed(int)}.
 */
@Component
public class EvalJudge {

    private static final Logger log = LoggerFactory.getLogger(EvalJudge.class);
    private static final String SCHEMA_NAME = "judge";
    private static final int MAX_COMMENT_CODE_POINTS = 100;

    private final ChatModel chatModel;
    private final PiiRedactor redactor;
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final JsonMapper mapper;
    private final Clock clock;

    public EvalJudge(ChatModel chatModel, PiiRedactor redactor, JsonMapper mapper, Clock clock) {
        this.chatModel = chatModel;
        this.redactor = redactor;
        this.mapper = mapper;
        this.clock = clock;
    }

    public JudgeVerdict judge(String question, String answer, List<String> rubric, CustomerContext customer) {
        int tokens = 0;
        try {
            ChatResponse response = chatModel.chat(request(question, answer, rubric));
            tokens = totalTokens(response.tokenUsage());
            return parse(response.aiMessage().text(), rubric, customer, tokens);
        } catch (RuntimeException e) {
            // Class name only: the message may echo the answer or model output.
            log.warn("eval judge failed code={} error={}", EvalErrors.JUDGE_FAILED, e.getClass().getSimpleName());
            return JudgeVerdict.failed(tokens);
        }
    }

    private ChatRequest request(String question, String answer, List<String> rubric) {
        ObjectNode user = mapper.createObjectNode();
        user.put("question", question);
        user.put("answer", answer);
        user.put("today", LocalDate.now(clock.withZone(TAIPEI)).toString());
        ArrayNode items = user.putArray("rubric");
        rubric.forEach(items::add);
        return ChatRequest.builder()
                .messages(SystemMessage.from(EvalJudgePrompt.TEXT), UserMessage.from(mapper.writeValueAsString(user)))
                .temperature(0.0)
                .responseFormat(responseFormat())
                .build();
    }

    private JudgeVerdict parse(String text, List<String> rubric, CustomerContext customer, int tokens) {
        JsonNode root = mapper.readTree(text);
        JsonNode refused = root.get("refused");
        JsonNode results = root.get("rubric");
        JsonNode comment = root.get("comment");
        if (refused == null || !refused.isBoolean() || results == null || !results.isArray()
                || comment == null || !comment.isString() || results.size() != rubric.size()) {
            return JudgeVerdict.failed(tokens);
        }
        Boolean[] met = new Boolean[rubric.size()];
        for (JsonNode item : results) {
            JsonNode index = item.get("index");
            JsonNode value = item.get("met");
            if (index == null || !index.isIntegralNumber() || value == null || !value.isBoolean()) {
                return JudgeVerdict.failed(tokens);
            }
            int i = index.asInt() - 1;
            if (i < 0 || i >= met.length || met[i] != null) {
                return JudgeVerdict.failed(tokens);
            }
            met[i] = value.asBoolean();
        }
        List<JudgeVerdict.RubricResult> ordered = new ArrayList<>();
        for (int i = 0; i < met.length; i++) {
            ordered.add(new JudgeVerdict.RubricResult(rubric.get(i), met[i]));
        }
        return new JudgeVerdict(refused.asBoolean(), List.copyOf(ordered), clean(comment.asString(), customer), null,
                tokens);
    }

    /** Same order as the chat answer: PiiRedactor first, then the self token becomes plain wording; then clip. */
    private String clean(String comment, CustomerContext customer) {
        String text = redactor.redact(comment, customer).replace(PiiRedactor.SELF, CustomerDeidentifier.SUBJECT);
        if (text.codePointCount(0, text.length()) <= MAX_COMMENT_CODE_POINTS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_COMMENT_CODE_POINTS));
    }

    private static int totalTokens(TokenUsage usage) {
        return usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount();
    }

    private static ResponseFormat responseFormat() {
        JsonObjectSchema item = JsonObjectSchema.builder()
                .addProperty("index", JsonIntegerSchema.builder().build())
                .addProperty("met", JsonBooleanSchema.builder().build())
                .required("index", "met")
                .additionalProperties(false)
                .build();
        JsonObjectSchema root = JsonObjectSchema.builder()
                .addProperty("refused", JsonBooleanSchema.builder().build())
                .addProperty("rubric", JsonArraySchema.builder().items(item).build())
                .addProperty("comment", JsonStringSchema.builder().build())
                .required("refused", "rubric", "comment")
                .additionalProperties(false)
                .build();
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder().name(SCHEMA_NAME).rootElement(root).build())
                .build();
    }
}
