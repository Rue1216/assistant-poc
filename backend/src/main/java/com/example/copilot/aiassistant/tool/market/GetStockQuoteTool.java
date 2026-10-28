package com.example.copilot.aiassistant.tool.market;

import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetStockQuoteTool extends AbstractMarketTool<GetStockQuoteTool.StockArgs> {

    public record StockArgs(@NotNull @Pattern(regexp = "^\\d{4,6}$") String stockCode) {
    }

    public record StockQuoteData(String stockCode, String name, String dataDate, BigDecimal open, BigDecimal high,
                                 BigDecimal low, BigDecimal close, BigDecimal change, Long tradeVolume,
                                 BigDecimal peRatio, BigDecimal dividendYield, BigDecimal pbRatio) {
    }

    public GetStockQuoteTool(TwseClient twse) {
        super(twse);
    }

    @Override
    public String name() {
        return "getStockQuote";
    }

    @Override
    public String purpose() {
        return "查詢上市個股行情";
    }

    @Override
    public ToolSpecification specification() {
        return spec(name(), "查詢上市個股最近一個交易日的收盤行情、本益比、殖利率與股價淨值比。需要股票代號，例如 2330。")
                .parameters(schema().addStringProperty("stockCode", "上市股票代號，例如 2330")
                        .required("stockCode").build()).build();
    }

    @Override
    public Class<StockArgs> argsType() {
        return StockArgs.class;
    }

    @Override
    public ToolResult execute(StockArgs args, ToolContext context) {
        List<Map<String, String>> quotes;
        try {
            quotes = twse.fetch(TwseClient.STOCK_DAY_ALL);
        } catch (TwseException e) {
            return failure(e);
        }
        List<Map<String, String>> valuations;
        try {
            valuations = twse.fetch(TwseClient.BWIBBU_ALL);
        } catch (TwseException e) {
            valuations = List.of(); // valuation is optional: still answer with the quote
        }
        Map<String, String> quote = find(quotes, args.stockCode());
        if (quote == null) {
            return ToolResult.failure(ToolStatus.ERROR, "STOCK_NOT_FOUND", "查無此上市股票代號");
        }
        Map<String, String> valuation = find(valuations, args.stockCode());
        LocalDate dataDate = TwseParsing.rocDate(get(quote, "Date"));
        BigDecimal volume = TwseParsing.number(get(quote, "TradeVolume"));
        StockQuoteData data = new StockQuoteData(args.stockCode(), companyName(get(quote, "Name")),
                displayDate(dataDate), TwseParsing.number(get(quote, "OpeningPrice")),
                TwseParsing.number(get(quote, "HighestPrice")), TwseParsing.number(get(quote, "LowestPrice")),
                TwseParsing.number(get(quote, "ClosingPrice")), TwseParsing.number(get(quote, "Change")),
                volume == null ? null : volume.longValue(),
                valuation == null ? null : TwseParsing.number(get(valuation, "PEratio")),
                valuation == null ? null : TwseParsing.number(get(valuation, "DividendYield")),
                valuation == null ? null : TwseParsing.number(get(valuation, "PBratio")));
        return ToolResult.success(data, sources(dataDate), citation("上市個股行情", target(data), excerpt(data), 1));
    }

    private static String target(StockQuoteData data) {
        return data.name() == null ? data.stockCode() : data.name() + " " + data.stockCode();
    }

    /** Only the values that exist; a missing valuation is left out rather than shown as empty. */
    private static String excerpt(StockQuoteData data) {
        List<String> parts = new ArrayList<>();
        if (data.close() != null) {
            parts.add("收盤 " + plain(data.close()));
        }
        if (data.change() != null) {
            parts.add("漲跌 " + signed(data.change()));
        }
        if (data.peRatio() != null) {
            parts.add("本益比 " + plain(data.peRatio()));
        }
        if (data.dividendYield() != null) {
            parts.add("殖利率 " + plain(data.dividendYield()) + "%");
        }
        return parts.isEmpty() ? null : String.join("、", parts);
    }

    private static Map<String, String> find(List<Map<String, String>> rows, String code) {
        return rows.stream().filter(r -> code.equals(r.get("Code"))).findFirst().orElse(null);
    }
}
