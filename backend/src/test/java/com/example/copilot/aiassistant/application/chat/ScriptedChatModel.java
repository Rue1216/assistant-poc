package com.example.copilot.aiassistant.application.chat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** A {@link ChatModel} that answers with pre-arranged responses in order and records every request it receives. */
public class ScriptedChatModel implements ChatModel {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Deque<Function<ChatRequest, ChatResponse>> script = new ArrayDeque<>();
    private final List<ChatRequest> requests = new ArrayList<>();

    public ScriptedChatModel reply(ChatResponse response) {
        return reply(request -> response);
    }

    /** A step that can inspect the request or cause side effects (for example advance a clock). */
    public ScriptedChatModel reply(Function<ChatRequest, ChatResponse> step) {
        script.add(step);
        return this;
    }

    public ScriptedChatModel fail(RuntimeException error) {
        return reply(request -> {
            throw error;
        });
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        requests.add(request);
        Function<ChatRequest, ChatResponse> step = script.poll();
        if (step == null) {
            throw new AssertionError("unexpected model call #" + requests.size());
        }
        return step.apply(request);
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    public static ChatResponse text(String text, int totalTokens) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).tokenUsage(new TokenUsage(totalTokens)).build();
    }

    /** A final answer in the answer schema: one paragraph block without sources per paragraph. */
    public static ChatResponse answer(int totalTokens, String... paragraphs) {
        return text(blocks(Arrays.stream(paragraphs).map(p -> block("paragraph", p)).toArray(String[]::new)),
                totalTokens);
    }

    /** The answer-schema JSON {@code {"blocks": [...]}} built from {@link #block} strings. */
    public static String blocks(String... blockJson) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode blocks = root.putArray("blocks");
        for (String block : blockJson) {
            blocks.add(JSON.readTree(block));
        }
        return JSON.writeValueAsString(root);
    }

    /** One answer-schema block; {@code sources} are citation ids such as S1. */
    public static String block(String type, String text, String... sources) {
        ObjectNode block = JSON.createObjectNode();
        block.put("type", type);
        block.put("text", text);
        ArrayNode ids = block.putArray("sources");
        Arrays.stream(sources).forEach(ids::add);
        return JSON.writeValueAsString(block);
    }

    public static ChatResponse toolCalls(int totalTokens, ToolExecutionRequest... calls) {
        return toolCalls(null, totalTokens, calls);
    }

    public static ChatResponse toolCalls(String text, int totalTokens, ToolExecutionRequest... calls) {
        AiMessage message = text == null ? AiMessage.from(Arrays.asList(calls))
                : AiMessage.from(text, Arrays.asList(calls));
        return ChatResponse.builder().aiMessage(message).tokenUsage(new TokenUsage(totalTokens)).build();
    }

    /** The same response with an input/output token split (total is their sum). */
    public static ChatResponse usage(ChatResponse response, int inputTokens, int outputTokens) {
        return ChatResponse.builder().aiMessage(response.aiMessage())
                .tokenUsage(new TokenUsage(inputTokens, outputTokens)).build();
    }

    public static ToolExecutionRequest call(String id, String name, String arguments) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }

    /** Every piece of text a message carries to the model, including echoed tool-call names and arguments. */
    public static List<String> texts(ChatMessage message) {
        List<String> texts = new ArrayList<>();
        switch (message) {
            case SystemMessage m -> texts.add(m.text());
            case UserMessage m -> texts.add(m.singleText());
            case AiMessage m -> {
                texts.add(m.text());
                for (ToolExecutionRequest r : m.toolExecutionRequests()) {
                    texts.add(r.id());
                    texts.add(r.name());
                    texts.add(r.arguments());
                }
            }
            case ToolExecutionResultMessage m -> {
                texts.add(m.id());
                texts.add(m.toolName());
                texts.add(m.text());
            }
            default -> throw new AssertionError("unexpected message type " + message.type());
        }
        texts.removeIf(t -> t == null);
        return texts;
    }
}
