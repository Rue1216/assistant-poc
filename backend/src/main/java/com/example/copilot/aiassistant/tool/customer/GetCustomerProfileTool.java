package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.CustomFlagging;
import com.example.copilot.aiassistant.port.c360.ProfileCard;
import com.example.copilot.aiassistant.port.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
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
public class GetCustomerProfileTool extends AbstractCustomerTool {

    private static final String ATTENTION = "應注意名單";
    private static final String SPECIAL_CARE = "特殊客群";

    public GetCustomerProfileTool(CustomerDataPort port) {
        super(port);
    }

    @Override
    public String name() {
        return "getCustomerProfile";
    }

    @Override
    public String purpose() {
        return "查詢顧客名片與註記";
    }

    @Override
    public ToolSpecification specification() {
        return noArgsSpec(name(), "查詢本顧客的去識別化名片：年齡區間、客群、主要往來分行、標籤，以及不宜行銷、客訴等註記。不需要參數。");
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return AccessLevel.LIMITED;
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        Optional<Customer360Record> customer = customer360(context);
        if (customer.isEmpty() || customer.get().customerProfile() == null) {
            return unavailable();
        }
        boolean full = context.customer().accessLevel() == AccessLevel.FULL;
        ProfileCard card = customer.get().customerProfile();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subject", CustomerDeidentifier.SUBJECT);
        String ageRange = ageRange(card.age());
        if (ageRange != null) {
            data.put("ageRange", ageRange);
        }
        if (card.segment() != null) {
            data.put("segment", card.segment());
        }
        if (!full) {
            data.put("accessNote", CustomerDeidentifier.LIMITED_NOTE);
            return success(data, "顧客名片");
        }
        if (card.primaryBranch() != null) {
            data.put("primaryBranch", card.primaryBranch());
        }
        Optional<RealTimeStatusRecord> realTime = realTime(context);
        if (realTime.isPresent()) {
            if (realTime.get().profilePhoto() != null && realTime.get().profilePhoto().tags() != null) {
                data.put("profileTags", realTime.get().profilePhoto().tags());
            }
            data.put("notes", notes(realTime.get()));
        } else {
            missing("realTime");
        }
        data.put("dataDate", customer.get().dataDate());
        return success(data, "顧客名片");
    }

    /** Null when the age is absent or not a number. */
    private static String ageRange(String age) {
        if (age == null) {
            return null;
        }
        try {
            return CustomerDeidentifier.ageRange(Integer.parseInt(age.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
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
}
