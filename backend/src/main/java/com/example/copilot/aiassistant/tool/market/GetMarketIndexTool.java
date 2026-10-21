package com.example.copilot.aiassistant.tool.market;

import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetMarketIndexTool extends AbstractMarketTool<NoArgs> {

    private static final List<String> INDEX_NAMES = List.of("發行量加權股價指數", "臺灣50指數", "寶島股價指數",
            "電子工業類指數", "金融保險類指數");

    public record IndexItem(String name, BigDecimal close, BigDecimal change, BigDecimal changePercent) {
    }

    public record MarketIndexData(String dataDate, List<IndexItem> indices) {
    }

    public GetMarketIndexTool(TwseClient twse) {
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
            rows = twse.fetch(TwseClient.MI_INDEX);
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
        return ToolResult.success(new MarketIndexData(isoDate(dataDate), items), sources(dataDate));
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
