package com.example.copilot.aiassistant.tool.customer.sections;

import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.MemberRankData;
import com.example.copilot.aiassistant.tool.customer.DisplayFormat;
import com.example.copilot.aiassistant.tool.customer.MemberRankTable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Section {@code memberRank}: tier, progress, gaps, downgrade guard and benefits; never the tier code. */
public final class MemberRankSection {

    private static final String RELATIONSHIP_NOTE = "此類顧客不適用等級進度與權益說明";

    private MemberRankSection() {
    }

    /** Null when the platform delivered no member rank. */
    public static Map<String, Object> render(Customer360Record r) {
        MemberRankData rank = r.memberRank();
        if (rank == null) {
            return null;
        }
        MemberRankTable.Tier tier = MemberRankTable.tierOf(rank.memberRankCode());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rank", tier.displayName());
        switch (tier) {
            case RELATIONSHIP -> data.put("note", RELATIONSHIP_NOTE);
            case PRIVATE -> {
                data.put("currentBenefits", tier.benefits());
                putIfNotNull(data, "alert", rank.upgradeOrDownGradeAlert());
            }
            default -> general(data, tier, rank);
        }
        return data;
    }

    private static void general(Map<String, Object> data, MemberRankTable.Tier tier, MemberRankData rank) {
        BigDecimal progress = SectionSupport.decimal(rank.progressPercent(), "progressPercent");
        BigDecimal segmentProgress = SectionSupport.decimal(rank.segmentProgressPercent(), "segmentProgressPercent");
        BigDecimal aum = SectionSupport.decimal(rank.customerAum(), "customerAum");
        BigDecimal segmentAum = SectionSupport.decimal(rank.segmentAverageAum(), "segmentAverageAum");
        if (progress != null) {
            data.put("progressPercent", DisplayFormat.percent(progress));
        }
        if (segmentProgress != null) {
            data.put("segmentProgressPercent", DisplayFormat.percent(segmentProgress));
        }
        putIfNotNull(data, "alert", rank.upgradeOrDownGradeAlert());
        if (aum != null) {
            data.put("customerAum", DisplayFormat.amount(aum));
        }
        if (segmentAum != null) {
            data.put("segmentAverageAum", DisplayFormat.amount(segmentAum));
        }
        if (tier.threshold() != null) {
            data.put("threshold", DisplayFormat.amount(tier.threshold()));
        }
        BigDecimal line = tier.downgradeLine();
        if (line != null) {
            data.put("downgradeLine", DisplayFormat.amount(line));
            if (aum != null) {
                // Exactly on the line is not a risk.
                boolean risk = aum.compareTo(line) < 0;
                data.put("downgradeRisk", risk);
                if (risk) {
                    data.put("gapToDowngradeLine", DisplayFormat.amount(line.subtract(aum)));
                }
            }
        }
        MemberRankTable.Tier next = tier.next();
        if (next != null) {
            data.put("nextRank", next.displayName());
            if (aum != null && next.threshold() != null && aum.compareTo(next.threshold()) < 0) {
                data.put("gapToNext", DisplayFormat.amount(next.threshold().subtract(aum)));
            }
        }
        data.put("currentBenefits", tier.benefits());
        if (next != null) {
            data.put("nextBenefits", next.benefits());
        }
    }

    private static void putIfNotNull(Map<String, Object> data, String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
