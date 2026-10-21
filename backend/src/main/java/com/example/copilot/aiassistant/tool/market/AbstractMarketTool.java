package com.example.copilot.aiassistant.tool.market;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.tool.AssistantTool;
import com.example.copilot.aiassistant.tool.Source;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Shared parts of the three TWSE market tools. */
abstract class AbstractMarketTool<A extends Record> implements AssistantTool<A>, MarketTool {

    private static final String SOURCE_NAME = "臺灣證券交易所 OpenAPI";
    private static final String SOURCE_URL = "https://openapi.twse.com.tw/";
    private static final int MAX_NAME_CHARS = 50;

    protected final TwseClient twse;

    AbstractMarketTool(TwseClient twse) {
        this.twse = twse;
    }

    static ToolSpecification.Builder spec(String name, String description) {
        return ToolSpecification.builder().name(name).description(description);
    }

    static JsonObjectSchema.Builder schema() {
        return JsonObjectSchema.builder().additionalProperties(false);
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(5);
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return AccessLevel.LIMITED;
    }

    static List<Source> sources(LocalDate dataDate) {
        return List.of(new Source(SOURCE_NAME, SOURCE_URL, isoDate(dataDate)));
    }

    static ToolResult failure(TwseException e) {
        if (TwseException.TIMEOUT.equals(e.errorCode())) {
            return ToolResult.failure(ToolStatus.TIMEOUT, TwseException.TIMEOUT, "市場資料查詢逾時");
        }
        return ToolResult.failure(ToolStatus.ERROR, TwseException.UNAVAILABLE, "市場資料暫時無法取得");
    }

    /** External text is untrusted: cap its length before it reaches the model. */
    static String shorten(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        return s.codePointCount(0, s.length()) <= maxChars ? s : s.substring(0, s.offsetByCodePoints(0, maxChars));
    }

    static String companyName(String text) {
        return shorten(text, MAX_NAME_CHARS);
    }

    static String isoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    static String get(Map<String, String> row, String key) {
        return row.get(key);
    }
}
