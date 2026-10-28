package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class GetBehaviorTool extends AbstractCustomerTool {

    private static final int TOP_CONSUMPTION = 3;

    public GetBehaviorTool(CustomerDataPort port) {
        super(port);
    }

    @Override
    public String name() {
        return "getBehavior";
    }

    @Override
    public String purpose() {
        return "查詢往來與行為";
    }

    @Override
    public ToolSpecification specification() {
        return noArgsSpec(name(), "查詢本顧客的帳戶往來深度、消費行為、通路使用與數位行為指數，並與同客群比較。不需要參數。");
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        Optional<Customer360Record> customer = customer360(context);
        if (customer.isEmpty()) {
            return unavailable();
        }
        Customer360Record c = customer.get();
        Map<String, Object> data = new LinkedHashMap<>();
        if (c.accountRelationshipDepth() != null) {
            Map<String, Object> depth = new LinkedHashMap<>();
            depth.put("items", RadarFacts.items(c.accountRelationshipDepth(), false, false));
            depth.put("aboveSegment", RadarFacts.aboveSegment(c.accountRelationshipDepth()));
            data.put("accountDepth", depth);
        } else {
            missing("accountRelationshipDepth");
        }
        if (c.consumptionBehavior() != null) {
            Map<String, Object> consumption = new LinkedHashMap<>();
            consumption.put("items", RadarFacts.items(c.consumptionBehavior(), false, false));
            consumption.put("top3", IntStream.rangeClosed(1, TOP_CONSUMPTION)
                    .mapToObj(rank -> RadarFacts.top(c.consumptionBehavior(), rank))
                    .filter(top -> top != null).map(top -> top.get("item")).toList());
            List<String> tags = c.consumptionBehavior().tagList();
            if (tags != null) {
                consumption.put("groupTags", tags);
            }
            data.put("consumption", consumption);
        } else {
            missing("consumptionBehavior");
        }
        if (c.channelBehavior() != null) {
            Map<String, Object> channel = new LinkedHashMap<>();
            channel.put("items", RadarFacts.items(c.channelBehavior(), false, false));
            Map<String, Object> mostUsed = RadarFacts.top(c.channelBehavior(), 1);
            if (mostUsed != null) {
                channel.put("mostUsed", mostUsed);
            }
            data.put("channel", channel);
        } else {
            missing("channelBehavior");
        }
        if (c.digitalBehavior() != null) {
            Map<String, Object> digital = new LinkedHashMap<>();
            digital.put("items", RadarFacts.items(c.digitalBehavior(), true, false));
            digital.put("aboveSegment", RadarFacts.aboveSegment(c.digitalBehavior()));
            digital.put("belowSegment", RadarFacts.belowSegment(c.digitalBehavior()));
            data.put("digital", digital);
        } else {
            missing("digitalBehavior");
        }
        if (data.isEmpty()) {
            return unavailable();
        }
        data.put("dataDate", c.dataDate());
        return success(data, "往來與行為");
    }
}
