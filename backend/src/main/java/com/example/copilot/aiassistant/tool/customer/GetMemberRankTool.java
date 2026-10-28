package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.MemberRankData;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GetMemberRankTool extends AbstractCustomerTool {

    private static final String RELATIONSHIP_NOTE = "此類顧客不適用等級進度與權益說明";

    public GetMemberRankTool(CustomerDataPort port) {
        super(port);
    }

    @Override
    public String name() {
        return "getMemberRank";
    }

    @Override
    public String purpose() {
        return "查詢理財會員等級";
    }

    @Override
    public ToolSpecification specification() {
        return noArgsSpec(name(), "查詢本顧客的理財會員等級、升降等進度、距下一級與防降等線的差距，以及目前與下一級權益。不需要參數。");
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        Optional<Customer360Record> customer = customer360(context);
        if (customer.isEmpty() || customer.get().memberRank() == null) {
            return unavailable();
        }
        MemberRankData rank = customer.get().memberRank();
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
        data.put("dataDate", customer.get().dataDate());
        return success(data, "會員等級");
    }

    private static void general(Map<String, Object> data, MemberRankTable.Tier tier, MemberRankData rank) {
        BigDecimal progress = decimal(rank.progressPercent(), "progressPercent");
        BigDecimal segmentProgress = decimal(rank.segmentProgressPercent(), "segmentProgressPercent");
        BigDecimal aum = decimal(rank.customerAum(), "customerAum");
        BigDecimal segmentAum = decimal(rank.segmentAverageAum(), "segmentAverageAum");
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
