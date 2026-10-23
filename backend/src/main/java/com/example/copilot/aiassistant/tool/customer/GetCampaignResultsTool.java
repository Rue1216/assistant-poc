package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetCampaignResultsTool extends AbstractCustomerTool<NoArgs> {

    public GetCampaignResultsTool(CustomerDataPort port) {
        super(port);
    }

    @Override
    public String name() {
        return "getCampaignResults";
    }

    @Override
    public String purpose() {
        return "查詢顧客的活動回應";
    }

    @Override
    public ToolSpecification specification() {
        return spec(name(), "查詢本顧客近期參與的行銷活動與回應結果。").parameters(schema().build()).build();
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
        List<Map<String, Object>> rows = port.findCampaignResults(context.customer().customerId()).stream()
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("campaignName", c.campaignName());
                    row.put("date", c.date().toString());
                    row.put("channel", c.channel());
                    row.put("response", c.response());
                    return row;
                }).toList();
        return ToolResult.success(Map.of("campaigns", rows), SOURCES,
                citation("行銷活動反應", rows.size() + " 筆", rows.size()));
    }
}
