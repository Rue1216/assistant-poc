package com.example.copilot.aiassistant.domain.tool.market;

import com.example.copilot.aiassistant.domain.tool.NoArgs;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetMarketIndexTool extends AbstractMarketTool<NoArgs> {

    private static final String TAIEX = "發行量加權股價指數";
    private static final int EXCERPT_ITEMS = 3;
    private static final List<String> INDEX_NAMES = List.of(TAIEX, "臺灣50指數", "寶島股價指數",
            "電子工業類指數", "金融保險類指數");

    public record IndexItem(String name, BigDecimal close, BigDecimal change, BigDecimal changePercent) {
    }

    public record MarketIndexData(String dataDate, List<IndexItem> indices) {
    }

    public GetMarketIndexTool(TwseGateway twse) {
        super(twse);
    }

    @Override
    public String name() {
        return "getMarketIndex";
    }

    @Override
    public String purpose() {
        return "查詢大盤指數";
    }

    @Override
    public ToolSpecification specification() {
        return spec(name(), "查詢台股最近一個交易日的主要指數收盤資訊（加權指數、臺灣50 等）。")
                .parameters(schema().build()).build();
    }

    @Override
    public Class<NoArgs> argsType() {
        return NoArgs.class;
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        List<Map<String, String>> rows;
        try {
            rows = twse.fetch(TwseGateway.MI_INDEX);
        } catch (TwseException e) {
            return failure(e);
        }
        LocalDate dataDate = null;
        List<IndexItem> items = new ArrayList<>();
        for (String indexName : INDEX_NAMES) {
            for (Map<String, String> row : rows) {
                if (indexName.equals(get(row, "指數"))) {
                    if (dataDate == null) {
                        dataDate = TwseParsing.rocDate(get(row, "日期"));
                    }
                    items.add(new IndexItem(indexName, TwseParsing.number(get(row, "收盤指數")), signedChange(row),
                            TwseParsing.number(get(row, "漲跌百分比"))));
                    break;
                }
            }
        }
        return ToolResult.success(new MarketIndexData(displayDate(dataDate), items), sources(dataDate),
                citation("大盤指數", null, excerpt(items), items.size()));
    }

    /** The first {@value #EXCERPT_ITEMS} indices, e.g. "加權指數 47111.26（漲跌 +141.39）". */
    private static String excerpt(List<IndexItem> items) {
        List<String> parts = new ArrayList<>();
        for (IndexItem item : items.subList(0, Math.min(EXCERPT_ITEMS, items.size()))) {
            String name = TAIEX.equals(item.name()) ? "加權指數" : item.name();
            String text = item.close() == null ? name : name + " " + plain(item.close());
            if (item.close() != null && item.change() != null) {
                text += "（漲跌 " + signed(item.change()) + "）";
            }
            parts.add(text);
        }
        return parts.isEmpty() ? null : String.join("、", parts);
    }

    private static BigDecimal signedChange(Map<String, String> row) {
        BigDecimal points = TwseParsing.number(get(row, "漲跌點數"));
        String sign = get(row, "漲跌");
        if (points != null && sign != null && "-".equals(sign.trim())) {
            return points.negate();
        }
        return points;
    }
}
