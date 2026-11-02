package com.example.copilot.aiassistant.domain.tool.customer;

import com.example.copilot.aiassistant.domain.customer.c360.AlertType;
import com.example.copilot.aiassistant.domain.customer.c360.CustomFlagging;
import com.example.copilot.aiassistant.domain.customer.c360.KeyAlert;
import com.example.copilot.aiassistant.domain.customer.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.tool.CitationDetail;
import com.example.copilot.aiassistant.domain.tool.NoArgs;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import com.example.copilot.aiassistant.domain.tool.customer.sections.SectionSupport;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@code getRealTimeCustomer()}: the real-time platform API: notes, profile tags and key alerts. */
public class GetRealTimeCustomerTool extends AbstractCustomerTool<NoArgs> {

    private static final String ATTENTION = "應注意名單";
    private static final String SPECIAL_CARE = "特殊客群";

    private final PiiRedactor redactor;

    public GetRealTimeCustomerTool(PiiRedactor redactor) {
        this.redactor = redactor;
    }

    @Override
    public String name() {
        return "getRealTimeCustomer";
    }

    @Override
    public String purpose() {
        return "查詢顧客即時註記與提醒";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder().name(name())
                .description("查詢本顧客的即時註記（不宜行銷、客訴、特殊客群、應注意名單與到期日）、畫像標籤與關鍵提醒。不需要參數。")
                .parameters(JsonObjectSchema.builder().additionalProperties(false).build()).build();
    }

    @Override
    public Class<NoArgs> argsType() {
        return NoArgs.class;
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        if (context.data() == null) {
            return unavailable();
        }
        Optional<RealTimeStatusRecord> status = context.data().realTime();
        if (status.isEmpty()) {
            return unavailable();
        }
        RealTimeStatusRecord realTime = status.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notes", notes(realTime));
        if (realTime.profilePhoto() != null && realTime.profilePhoto().tags() != null) {
            data.put("profileTags", realTime.profilePhoto().tags());
        }
        data.put("keyAlerts", keyAlerts(realTime, context.customer()));
        return ToolResult.success(data, SOURCES,
                new CitationDetail(TITLE_PREFIX + "即時註記與提醒", null, null, "註記、畫像標籤、關鍵提醒", 3));
    }

    private static List<Map<String, Object>> notes(RealTimeStatusRecord realTime) {
        List<Map<String, Object>> notes = new ArrayList<>();
        if (realTime.attentionCustomer()) {
            notes.add(note(ATTENTION, null));
        }
        if (realTime.specialCareCustomer()) {
            notes.add(note(SPECIAL_CARE, null));
        }
        if (realTime.customerFlaggingList() != null) {
            for (CustomFlagging flag : realTime.customerFlaggingList()) {
                if (flag.type() != null) {
                    notes.add(note(flag.type().displayName(), flag.expiryDate()));
                }
            }
        }
        return notes;
    }

    private static Map<String, Object> note(String category, String expiryDate) {
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("category", category);
        if (expiryDate != null) {
            note.put("expiryDate", expiryDate);
        }
        return note;
    }

    private List<Map<String, Object>> keyAlerts(RealTimeStatusRecord realTime, CustomerContext who) {
        if (realTime.keyAlerts() == null || realTime.keyAlerts().items() == null) {
            SectionSupport.missing("keyAlerts");
            return List.of();
        }
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (KeyAlert alert : realTime.keyAlerts().items()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("category", (alert.type() == null ? AlertType.OTHER : alert.type()).displayName());
            out.put("title", SectionSupport.free(redactor, alert.title(), who));
            out.put("detail", SectionSupport.free(redactor, alert.detail(), who));
            out.put("date", alert.date());
            alerts.add(out);
        }
        return alerts;
    }
}
