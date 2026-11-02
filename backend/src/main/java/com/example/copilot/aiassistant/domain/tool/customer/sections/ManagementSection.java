package com.example.copilot.aiassistant.domain.tool.customer.sections;

import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.customer.c360.ManagementCard;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.customer.DisplayFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section {@code management}: campaigns, recommended products and the management insight. */
public final class ManagementSection {

    private final PiiRedactor redactor;

    public ManagementSection(PiiRedactor redactor) {
        this.redactor = redactor;
    }

    /** Null when the platform delivered no management block. */
    public Map<String, Object> render(Customer360Record r, ToolContext ctx) {
        ManagementCard management = r.customerManagement();
        if (management == null) {
            return null;
        }
        CustomerContext who = ctx.customer();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaigns", campaigns(management, who));
        if (management.recommendedProducts() != null) {
            out.put("recommendedProducts", management.recommendedProducts());
        }
        if (management.insight() != null) {
            out.put("insight", SectionSupport.free(redactor, management.insight(), who));
        }
        return out;
    }

    private List<Map<String, Object>> campaigns(ManagementCard management, CustomerContext who) {
        List<Map<String, Object>> campaigns = new ArrayList<>();
        if (management.campaignList() == null) {
            return campaigns;
        }
        management.campaignList().forEach(c -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", c.campaignName());
            out.put("duration", c.campaignDuration());
            out.put("summary", SectionSupport.free(redactor, c.campaignSummary(), who));
            out.put("digitalDelivery", DisplayFormat.yesNo("Y".equals(c.campaignEnabled())));
            campaigns.add(out);
        });
        return campaigns;
    }
}
