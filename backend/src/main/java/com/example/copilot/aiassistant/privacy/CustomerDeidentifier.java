package com.example.copilot.aiassistant.privacy;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured-field de-identification (03 section 1.6, layer one). Stateless. */
public final class CustomerDeidentifier {

    public static final String SUBJECT = "本顧客";
    public static final String LIMITED_NOTE = "依您的權限僅能查看去識別化基本輪廓";

    private static final long WAN = 10_000L;

    private CustomerDeidentifier() {
    }

    public static String ageRange(LocalDate birthDate, LocalDate today) {
        int age = Period.between(birthDate, today).getYears();
        if (age < 25) {
            return "18-24";
        } else if (age < 35) {
            return "25-34";
        } else if (age < 45) {
            return "35-44";
        } else if (age < 55) {
            return "45-54";
        } else if (age < 65) {
            return "55-64";
        }
        return "65+";
    }

    public static String assetTier(long amount) {
        if (amount < 100 * WAN) {
            return "100萬以下";
        } else if (amount < 300 * WAN) {
            return "100萬-300萬";
        } else if (amount < 500 * WAN) {
            return "300萬-500萬";
        } else if (amount < 1000 * WAN) {
            return "500萬-1000萬";
        }
        return "1000萬以上";
    }

    /** 10% bands: 0.25 gives 20%-30%; a full 100% falls in the top band 90%-100%. */
    public static String weightRange(BigDecimal weight) {
        int band = weight.movePointRight(1).setScale(0, RoundingMode.FLOOR).intValue();
        int lower = Math.max(0, Math.min(band, 9)) * 10;
        return lower + "%-" + (lower + 10) + "%";
    }

    public static Map<String, Object> profile(CustomerRecord r, AccessLevel level, LocalDate today) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", SUBJECT);
        out.put("segment", r.segment());
        out.put("ageRange", ageRange(r.birthDate(), today));
        out.put("relationshipYears", r.relationshipYears());
        if (level == AccessLevel.FULL) {
            out.put("assetTier", assetTier(r.assetAmount()));
            out.put("riskProfile", r.riskProfile());
            out.put("marketingConsent", r.marketingConsent());
            out.put("preferredChannel", r.preferredChannel());
            out.put("tags", r.tags());
        } else {
            out.put("accessNote", LIMITED_NOTE);
        }
        return out;
    }
}
