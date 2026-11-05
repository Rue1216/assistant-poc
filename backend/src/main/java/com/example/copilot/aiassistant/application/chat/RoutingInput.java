package com.example.copilot.aiassistant.application.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Package-private: the question, summary and recent history both light model calls send. */
final class RoutingInput {

    private static final int MAX_HISTORY = 4;
    private static final int MAX_HISTORY_CODE_POINTS = 500;

    private RoutingInput() {
    }

    /** A JSON object with question, summary (null when absent) and history; callers add their own fields. */
    static ObjectNode of(JsonMapper mapper, String question, List<ChatMessage> history, String summary,
                         int summaryMaxChars) {
        ObjectNode user = mapper.createObjectNode();
        user.put("question", question);
        if (summary == null) {
            user.putNull("summary");
        } else {
            user.put("summary", clip(summary, summaryMaxChars));
        }
        ArrayNode historyNode = user.putArray("history");
        List<ChatMessage> recent = history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size());
        for (ChatMessage message : recent) {
            switch (message) {
                case UserMessage m -> historyNode.add(turn(mapper, "user", m.singleText()));
                case AiMessage m -> historyNode.add(turn(mapper, "assistant", m.text()));
                default -> { }
            }
        }
        return user;
    }

    private static ObjectNode turn(JsonMapper mapper, String role, String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", role);
        node.put("content", clip(text == null ? "" : text, MAX_HISTORY_CODE_POINTS));
        return node;
    }

    /** Keeps at most {@code maxCodePoints} code points, so a surrogate pair is never split. */
    private static String clip(String text, int maxCodePoints) {
        if (text.codePointCount(0, text.length()) > maxCodePoints) {
            return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
        }
        return text;
    }
}
