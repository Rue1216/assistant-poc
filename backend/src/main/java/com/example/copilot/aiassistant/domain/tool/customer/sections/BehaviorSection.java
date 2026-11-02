package com.example.copilot.aiassistant.domain.tool.customer.sections;

import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.tool.customer.RadarFacts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Section {@code behavior}: account depth, consumption, channel and digital behavior. */
public final class BehaviorSection {

    private static final int TOP_CONSUMPTION = 3;

    private BehaviorSection() {
    }

    /** Null when the platform delivered none of the four behavior blocks. */
    public static Map<String, Object> render(Customer360Record c) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (c.accountRelationshipDepth() != null) {
            Map<String, Object> depth = new LinkedHashMap<>();
            depth.put("items", RadarFacts.items(c.accountRelationshipDepth(), false, false));
            depth.put("aboveSegment", RadarFacts.aboveSegment(c.accountRelationshipDepth()));
            data.put("accountDepth", depth);
        } else {
            SectionSupport.missing("accountRelationshipDepth");
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
            SectionSupport.missing("consumptionBehavior");
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
            SectionSupport.missing("channelBehavior");
        }
        if (c.digitalBehavior() != null) {
            Map<String, Object> digital = new LinkedHashMap<>();
            digital.put("items", RadarFacts.items(c.digitalBehavior(), true, false));
            digital.put("aboveSegment", RadarFacts.aboveSegment(c.digitalBehavior()));
            digital.put("belowSegment", RadarFacts.belowSegment(c.digitalBehavior()));
            data.put("digital", digital);
        } else {
            SectionSupport.missing("digitalBehavior");
        }
        return data.isEmpty() ? null : data;
    }
}
