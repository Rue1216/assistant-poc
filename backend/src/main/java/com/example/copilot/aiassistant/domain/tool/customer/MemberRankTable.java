package com.example.copilot.aiassistant.domain.tool.customer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Wealth-member rank codes mapped to tiers, thresholds (in yuan) and benefits.
 * 示意，待對照平台權益文案 (benefit texts are illustrative until checked against the platform wording).
 */
@Slf4j
public final class MemberRankTable {

    public enum Tier {
        NONE("非理財會員", null, null, List.of()),
        NEW_RICH("新富", wan(300), wan(350), List.of("專屬理專服務", "跨行轉帳免費 10 次／月", "理財講座優先報名")),
        ELITE("菁英", wan(1000), wan(1200), List.of("基金申購手續費 5 折", "機場貴賓室 4 次／年", "年度健康檢查")),
        SUMMIT("登峰", wan(3000), wan(3300), List.of("專屬財富管理團隊", "機場貴賓室 8 次／年", "年度健康檢查（含眷屬 1 名）")),
        PRIVATE("極致／私銀", null, null, List.of("私人銀行家服務", "家族傳承諮詢", "機場接送 4 次／年")),
        RELATIONSHIP("關係戶／親子戶", null, null, List.of());

        private final String displayName;
        private final BigDecimal threshold;
        private final BigDecimal downgradeLine;
        private final List<String> benefits;

        Tier(String displayName, BigDecimal threshold, BigDecimal downgradeLine, List<String> benefits) {
            this.displayName = displayName;
            this.threshold = threshold;
            this.downgradeLine = downgradeLine;
            this.benefits = benefits;
        }

        public String displayName() {
            return displayName;
        }

        /** Entry threshold in yuan, or null when not defined. */
        public BigDecimal threshold() {
            return threshold;
        }

        /** Downgrade-protection line in yuan, or null when not defined. */
        public BigDecimal downgradeLine() {
            return downgradeLine;
        }

        /** The next tier up, or null for the top tier and for relationship accounts. */
        public Tier next() {
            return switch (this) {
                case NONE -> NEW_RICH;
                case NEW_RICH -> ELITE;
                case ELITE -> SUMMIT;
                case SUMMIT -> PRIVATE;
                case PRIVATE, RELATIONSHIP -> null;
            };
        }

        public List<String> benefits() {
            return benefits;
        }

        private static BigDecimal wan(long tenThousand) {
            return BigDecimal.valueOf(tenThousand * 10_000L);
        }
    }

    private static final Map<String, Tier> CODES = Map.ofEntries(
            Map.entry("0", Tier.NONE),
            Map.entry("1", Tier.NONE),
            Map.entry("2", Tier.NONE),
            Map.entry("3", Tier.NONE),
            Map.entry("Z", Tier.NONE),
            Map.entry("P", Tier.NEW_RICH),
            Map.entry("Q", Tier.NEW_RICH),
            Map.entry("N", Tier.ELITE),
            Map.entry("O", Tier.ELITE),
            Map.entry("M", Tier.SUMMIT),
            Map.entry("V", Tier.PRIVATE),
            Map.entry("K", Tier.PRIVATE),
            Map.entry("R", Tier.RELATIONSHIP),
            Map.entry("W", Tier.RELATIONSHIP),
            Map.entry("F", Tier.RELATIONSHIP),
            Map.entry("G", Tier.RELATIONSHIP),
            Map.entry("H", Tier.RELATIONSHIP),
            Map.entry("I", Tier.RELATIONSHIP),
            Map.entry("J", Tier.RELATIONSHIP));

    private MemberRankTable() {}

    /** Null or unknown codes map to NONE; an unknown code is logged without the code itself. */
    public static Tier tierOf(String code) {
        if (code == null) {
            return Tier.NONE;
        }
        Tier tier = CODES.get(code);
        if (tier == null) {
            log.warn("member rank code unknown");
            return Tier.NONE;
        }
        return tier;
    }
}
