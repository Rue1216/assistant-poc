package com.example.copilot.aiassistant.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.chat.ScriptedChatModel;
import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.llm.LlmErrorMapper;
import com.example.copilot.testsupport.LogCapture;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class InstructionRouterTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private static final List<AssistantConfig> INSTRUCTIONS = List.of(
            config("instruction.contact-prep", "聯繫準備",
                    "## 適用問題\n  使用者準備聯繫本顧客，想回顧近期互動。  \n\n## 回答方式\n先摘要再列重點"),
            config("instruction.market-brief", "市場快訊", "## 回答方式\n只列重點"));

    private static AssistantConfig config(String key, String name, String content) {
        AssistantConfig c = new AssistantConfig();
        c.setConfigKey(key);
        c.setName(name);
        c.setContent(content);
        return c;
    }

    private InstructionRouter router(ScriptedChatModel model) {
        return new InstructionRouter(model, new LlmErrorMapper(), mapper);
    }

    private RouteDecision route(ScriptedChatModel model) {
        return router(model).route("【本顧客】最近有什麼互動？", List.of(), INSTRUCTIONS);
    }

    @Test
    void requestShape() {
        ScriptedChatModel model = new ScriptedChatModel()
                .reply(ScriptedChatModel.text("{\"category\":\"none\"}", 30));
        List<ChatMessage> history = new ArrayList<>();
        history.add(UserMessage.from("第一則被捨棄"));
        history.add(AiMessage.from("a2"));
        history.add(UserMessage.from("u3"));
        history.add(AiMessage.from("a".repeat(499) + "😀額外"));
        history.add(UserMessage.from("u5"));

        router(model).route("【本顧客】最近有什麼互動？", history, INSTRUCTIONS);

        ChatRequest request = model.requests().getFirst();
        assertThat(request.messages()).hasSize(2);
        assertThat(((SystemMessage) request.messages().get(0)).text()).isEqualTo(RouterPrompt.TEXT);
        assertThat(request.temperature()).isEqualTo(0.0);
        assertThat(request.toolSpecifications()).isNullOrEmpty();

        JsonNode user = mapper.readTree(((UserMessage) request.messages().get(1)).singleText());
        assertThat(user.get("question").asString()).isEqualTo("【本顧客】最近有什麼互動？");
        JsonNode h = user.get("history");
        assertThat(h).hasSize(4);
        assertThat(h.get(0).get("role").asString()).isEqualTo("assistant");
        assertThat(h.get(0).get("content").asString()).isEqualTo("a2");
        assertThat(h.get(1).get("role").asString()).isEqualTo("user");
        assertThat(h.get(2).get("role").asString()).isEqualTo("assistant");
        String clipped = h.get(2).get("content").asString();
        assertThat(clipped.codePointCount(0, clipped.length())).isEqualTo(500);
        assertThat(clipped).endsWith("😀");
        assertThat(h.get(3).get("content").asString()).isEqualTo("u5");

        JsonNode categories = user.get("categories");
        assertThat(categories).hasSize(2);
        assertThat(categories.get(0).get("key").asString()).isEqualTo("instruction.contact-prep");
        assertThat(categories.get(0).get("name").asString()).isEqualTo("聯繫準備");
        assertThat(categories.get(0).get("scope").asString()).isEqualTo("使用者準備聯繫本顧客，想回顧近期互動。");
        // No scope section: the name stands in.
        assertThat(categories.get(1).get("scope").asString()).isEqualTo("市場快訊");

        assertThat(request.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        JsonObjectSchema root = (JsonObjectSchema) request.responseFormat().jsonSchema().rootElement();
        assertThat(root.required()).containsExactly("category");
        assertThat(root.additionalProperties()).isFalse();
        assertThat(((JsonEnumSchema) root.properties().get("category")).enumValues())
                .containsExactlyInAnyOrder("instruction.contact-prep", "instruction.market-brief", "none");
    }

    @Test
    void picksCategory() {
        ScriptedChatModel model = new ScriptedChatModel().reply(ScriptedChatModel.usage(
                ScriptedChatModel.text("{\"category\":\"instruction.contact-prep\"}", 0), 120, 8));

        RouteDecision decision = route(model);

        assertThat(decision).isEqualTo(new RouteDecision("instruction.contact-prep", RouteSource.ROUTER, true,
                120, 8, 128));
        assertThat(decision.selection()).isEqualTo(InstructionSelection.only("instruction.contact-prep"));
    }

    @Test
    void noneMeansNoInstruction() {
        RouteDecision decision = route(new ScriptedChatModel()
                .reply(ScriptedChatModel.text("{\"category\":\"none\"}", 30)));

        assertThat(decision.instructionKey()).isNull();
        assertThat(decision.source()).isEqualTo(RouteSource.ROUTER);
        assertThat(decision.selection().kind()).isEqualTo(InstructionSelection.Kind.NONE);
    }

    @Test
    void unknownKeyIsFallback() {
        RouteDecision decision = route(new ScriptedChatModel()
                .reply(ScriptedChatModel.text("{\"category\":\"instruction.other\"}", 30)));

        assertThat(decision.source()).isEqualTo(RouteSource.FALLBACK);
        assertThat(decision.instructionKey()).isNull();
        assertThat(decision.selection().kind()).isEqualTo(InstructionSelection.Kind.ALL);
    }

    @Test
    void badOutputIsFallback() {
        RouteDecision notJson = route(new ScriptedChatModel().reply(ScriptedChatModel.text("不是 JSON", 30)));
        RouteDecision missing = route(new ScriptedChatModel().reply(ScriptedChatModel.text("{\"x\":1}", 30)));
        RouteDecision thrown = route(new ScriptedChatModel().fail(new IllegalStateException("boom")));

        assertThat(List.of(notJson, missing, thrown)).allSatisfy(d -> {
            assertThat(d.source()).isEqualTo(RouteSource.FALLBACK);
            assertThat(d.instructionKey()).isNull();
        });
        assertThat(missing.modelCalled()).isTrue();
        assertThat(missing.totalTokens()).isEqualTo(30);
        assertThat(thrown.modelCalled()).isTrue();
        assertThat(thrown.totalTokens()).isZero();
    }

    @Test
    void noInstructionsSkipsModel() {
        ScriptedChatModel model = new ScriptedChatModel();

        RouteDecision decision = router(model).route("問題", List.of(), List.of());

        assertThat(decision).isEqualTo(new RouteDecision(null, RouteSource.ROUTER, false, 0, 0, 0));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void failureLogHasCodeOnly() {
        try (LogCapture capture = LogCapture.of(InstructionRouter.class, Level.WARN)) {
            router(new ScriptedChatModel().fail(new IllegalStateException("提問內容子丑寅")))
                    .route("提問內容子丑寅", List.of(), INSTRUCTIONS);

            List<String> lines = capture.events().stream().map(LogCapture::render).toList();
            assertThat(lines).hasSize(1);
            assertThat(lines.getFirst()).contains("chat route failed requestId=", "code=AI_LLM_UNAVAILABLE")
                    .doesNotContain("提問內容子丑寅");
        }
    }
}
