package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetCustomerHoldingsTool extends AbstractCustomerTool<NoArgs> {

    public GetCustomerHoldingsTool(CustomerDataPort port) {
        super(port);
    }

    @Override
    public String name() {
        return "getCustomerHoldings";
    }

    @Override
    public String purpose() {
        return "查詢顧客持有商品概況";
    }

    @Override
    public ToolSpecification specification() {
        return spec(name(), "查詢本顧客目前持有的商品類別與占比區間。上市股票會附股票代號，可再用 getStockQuote 查行情。不需要參數。")
                .parameters(schema().build()).build();
    }

    @Override
    public Class<NoArgs> argsType() {
        return NoArgs.class;
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        if (customer(context).isEmpty()) {
            return unavailable();
        }
        List<Map<String, Object>> holdings = port.findHoldings(context.customer().customerId()).stream().map(h -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productType", h.productType());
            row.put("productName", h.productName());
            row.put("stockCode", h.stockCode());
            row.put("weightRange", CustomerDeidentifier.weightRange(h.weight()));
            return row;
        }).toList();
        return ToolResult.success(Map.of("holdings", holdings), SOURCES);
    }
}
