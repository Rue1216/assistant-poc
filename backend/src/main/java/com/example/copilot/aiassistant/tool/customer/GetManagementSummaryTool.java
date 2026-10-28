package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.c360.AlertType;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.KeyAlert;
import com.example.copilot.aiassistant.port.c360.ManagementCard;
import com.example.copilot.aiassistant.port.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GetManagementSummaryTool extends AbstractCustomerTool {

    private static final int MAX_TEXT_CODE_POINTS = 200;

    private final PiiRedactor redactor;

    public GetManagementSummaryTool(CustomerDataPort port, PiiRedactor redactor) {
        super(port);
        this.redactor = redactor;
    }

    @Override
    public String name() {
        return "getManagementSummary";
    }

    @Override
    public String purpose() {
        return "查詢關鍵提醒與專屬活動";
    }

    @Override
    public ToolSpecification specification() {
        return noArgsSpec(name(), "查詢本顧客的關鍵提醒、專屬活動、建議商品與經營洞察。不需要參數。");
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        Optional<Customer360Record> customer = customer360(context);
        if (customer.isEmpty()) {
            return unavailable();
        }
        ManagementCard management = customer.get().customerManagement();
        Optional<RealTimeStatusRecord> realTime = realTime(context);
        if (management == null && realTime.isEmpty()) {
            return unavailable();
        }
        CustomerContext who = context.customer();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyAlerts", keyAlerts(realTime, who));
        if (management != null) {
            data.put("campaigns", campaigns(management, who));
            if (management.recommendedProducts() != null) {
                data.put("recommendedProducts", management.recommendedProducts());
            }
            if (management.insight() != null) {
                data.put("insight", free(management.insight(), who));
            }
        } else {
            missing("customerManagement");
        }
        data.put("dataDate", customer.get().dataDate());
        return success(data, "顧客經營");
    }

    private List<Map<String, Object>> keyAlerts(Optional<RealTimeStatusRecord> realTime, CustomerContext who) {
        if (realTime.isEmpty()) {
            missing("realTime");
            return List.of();
        }
        if (realTime.get().keyAlerts() == null || realTime.get().keyAlerts().items() == null) {
            missing("keyAlerts");
            return List.of();
        }
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (KeyAlert alert : realTime.get().keyAlerts().items()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("category", (alert.type() == null ? AlertType.OTHER : alert.type()).displayName());
            out.put("title", free(alert.title(), who));
            out.put("detail", free(alert.detail(), who));
            out.put("date", alert.date());
            alerts.add(out);
        }
        return alerts;
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
            out.put("summary", free(c.campaignSummary(), who));
            out.put("digitalDelivery", DisplayFormat.yesNo("Y".equals(c.campaignEnabled())));
            campaigns.add(out);
        });
        return campaigns;
    }

    /** Free text from the platform: redacted first, then cut, so a mask token is never split. */
    private String free(String text, CustomerContext who) {
        if (text == null) {
            return null;
        }
        String redacted = redactor.redact(text, who);
        if (redacted.codePointCount(0, redacted.length()) <= MAX_TEXT_CODE_POINTS) {
            return redacted;
        }
        return redacted.substring(0, redacted.offsetByCodePoints(0, MAX_TEXT_CODE_POINTS));
    }
}
