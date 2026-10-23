package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.InteractionRecord;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetRecentInteractionsTool extends AbstractCustomerTool<GetRecentInteractionsTool.InteractionArgs> {

    static final int DEFAULT_LIMIT = 5;
    static final int SUMMARY_MAX_CHARS = 100;
    private static final DateTimeFormatter LATEST_DATE = DateTimeFormatter.ofPattern("MM/dd");

    public record InteractionArgs(@Min(1) @Max(10) Integer limit) {
    }

    private final PiiRedactor redactor;

    public GetRecentInteractionsTool(CustomerDataPort port, PiiRedactor redactor) {
        super(port);
        this.redactor = redactor;
    }

    @Override
    public String name() {
        return "getRecentInteractions";
    }

    @Override
    public String purpose() {
        return "查詢近期互動紀錄";
    }

    @Override
    public ToolSpecification specification() {
        return spec(name(), "查詢本顧客最近的互動紀錄（日期、管道、主題、摘要、結果），由新到舊。")
                .parameters(schema().addIntegerProperty("limit", "回傳筆數，1 到 10，預設 5").build()).build();
    }

    @Override
    public Class<InteractionArgs> argsType() {
        return InteractionArgs.class;
    }

    @Override
    public ToolResult execute(InteractionArgs args, ToolContext context) {
        if (customer(context).isEmpty()) {
            return unavailable();
        }
        int limit = args.limit() == null ? DEFAULT_LIMIT : args.limit();
        List<InteractionRecord> records = port.findRecentInteractions(context.customer().customerId(), limit);
        List<Map<String, Object>> rows = records.stream().map(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", i.date().toString());
                    row.put("channel", i.channel());
                    row.put("topic", i.topic());
                    row.put("summary", truncate(redactor.redact(i.summary(), context.customer())));
                    row.put("outcome", i.outcome());
                    return row;
                }).toList();
        String description = records.isEmpty() ? "0 筆"
                : records.size() + " 筆，最近一筆 " + records.get(0).date().format(LATEST_DATE);
        return ToolResult.success(Map.of("interactions", rows), SOURCES,
                citation("近期互動紀錄", description, rows.size()));
    }

    private static String truncate(String text) {
        if (text == null || text.codePointCount(0, text.length()) <= SUMMARY_MAX_CHARS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, SUMMARY_MAX_CHARS));
    }
}
