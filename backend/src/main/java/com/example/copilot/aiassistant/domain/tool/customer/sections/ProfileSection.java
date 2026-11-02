package com.example.copilot.aiassistant.domain.tool.customer.sections;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.customer.c360.ProfileCard;
import com.example.copilot.aiassistant.domain.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import java.util.LinkedHashMap;
import java.util.Map;

/** Section {@code profile}: the de-identified customer card. The primary branch is for FULL access only. */
public final class ProfileSection {

    private ProfileSection() {
    }

    /** Null when the platform delivered no profile. */
    public static Map<String, Object> render(Customer360Record r, ToolContext ctx) {
        ProfileCard card = r.customerProfile();
        if (card == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", CustomerDeidentifier.SUBJECT);
        String ageRange = ageRange(card.age());
        if (ageRange != null) {
            out.put("ageRange", ageRange);
        }
        if (card.segment() != null) {
            out.put("segment", card.segment());
        }
        if (ctx.customer().accessLevel() == AccessLevel.FULL && card.primaryBranch() != null) {
            out.put("primaryBranch", card.primaryBranch());
        }
        return out;
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
}
