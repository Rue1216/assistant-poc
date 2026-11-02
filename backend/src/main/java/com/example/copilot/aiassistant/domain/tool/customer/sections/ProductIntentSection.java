package com.example.copilot.aiassistant.domain.tool.customer.sections;

import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.customer.c360.RadarView;
import com.example.copilot.aiassistant.domain.tool.customer.RadarFacts;
import java.util.LinkedHashMap;
import java.util.Map;

/** Section {@code productIntent}: wealth potential and the asset / liability-FX intent indexes. */
public final class ProductIntentSection {

    private ProductIntentSection() {
    }

    /** Null when the platform delivered none of the three radar blocks. */
    public static Map<String, Object> render(Customer360Record c) {
        if (c.wealthPotential() == null && c.assetProduct() == null && c.liabilitiesAndFx() == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (c.wealthPotential() != null) {
            Map<String, Object> wealth = new LinkedHashMap<>();
            wealth.put("items", RadarFacts.items(c.wealthPotential(), true, false));
            wealth.put("aboveSegment", RadarFacts.aboveSegment(c.wealthPotential()));
            data.put("wealthPotential", wealth);
        } else {
            SectionSupport.missing("wealthPotential");
        }
        putIntent(data, "assetIntent", c.assetProduct(), "assetProduct");
        putIntent(data, "liabilityFxIntent", c.liabilitiesAndFx(), "liabilitiesAndFx");
        return data;
    }

    private static void putIntent(Map<String, Object> data, String key, RadarView view, String block) {
        if (view == null) {
            SectionSupport.missing(block);
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
