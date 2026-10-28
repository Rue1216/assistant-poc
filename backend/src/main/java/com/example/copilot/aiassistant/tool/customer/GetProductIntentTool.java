package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.RadarView;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GetProductIntentTool extends AbstractCustomerTool {

    public GetProductIntentTool(CustomerDataPort port) {
        super(port);
    }

    @Override
    public String name() {
        return "getProductIntent";
    }

    @Override
    public String purpose() {
        return "查詢財富潛力與產品意圖";
    }

    @Override
    public ToolSpecification specification() {
        return noArgsSpec(name(), "查詢本顧客的財富潛力、資產產品意圖與負債外匯意圖指數，並與同客群比較。不需要參數。");
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        Optional<Customer360Record> customer = customer360(context);
        if (customer.isEmpty()) {
            return unavailable();
        }
        Customer360Record c = customer.get();
        if (c.wealthPotential() == null && c.assetProduct() == null && c.liabilitiesAndFx() == null) {
            return unavailable();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (c.wealthPotential() != null) {
            Map<String, Object> wealth = new LinkedHashMap<>();
            wealth.put("items", RadarFacts.items(c.wealthPotential(), true, false));
            wealth.put("aboveSegment", RadarFacts.aboveSegment(c.wealthPotential()));
            data.put("wealthPotential", wealth);
        } else {
            missing("wealthPotential");
        }
        putIntent(data, "assetIntent", c.assetProduct(), "assetProduct");
        putIntent(data, "liabilityFxIntent", c.liabilitiesAndFx(), "liabilitiesAndFx");
        data.put("dataDate", c.dataDate());
        return success(data, "產品意圖");
    }

    private static void putIntent(Map<String, Object> data, String key, RadarView view, String block) {
        if (view == null) {
            missing(block);
            return;
        }
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("items", RadarFacts.items(view, false, true));
        Map<String, Object> strongest = RadarFacts.top(view, 1);
        Map<String, Object> second = RadarFacts.top(view, 2);
        if (strongest != null) {
            intent.put("strongest", strongest);
        }
        if (second != null) {
            intent.put("second", second);
        }
        data.put(key, intent);
    }
}
