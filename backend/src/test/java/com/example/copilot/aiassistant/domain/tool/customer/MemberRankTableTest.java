package com.example.copilot.aiassistant.domain.tool.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.tool.customer.MemberRankTable.Tier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MemberRankTableTest {

    @Test
    void nonMemberCodes() {
        for (String code : new String[] {"0", "1", "2", "3", "Z", null, "X"}) {
            assertThat(MemberRankTable.tierOf(code)).as("code %s", code).isEqualTo(Tier.NONE);
        }
    }

    @Test
    void tiersAndThresholds() {
        assertThat(MemberRankTable.tierOf("P")).isEqualTo(Tier.NEW_RICH);
        assertThat(MemberRankTable.tierOf("Q")).isEqualTo(Tier.NEW_RICH);
        assertThat(Tier.NEW_RICH.threshold()).isEqualByComparingTo(new BigDecimal("3000000"));
        assertThat(Tier.NEW_RICH.downgradeLine()).isEqualByComparingTo(new BigDecimal("3500000"));
        assertThat(MemberRankTable.tierOf("N")).isEqualTo(Tier.ELITE);
        assertThat(MemberRankTable.tierOf("O")).isEqualTo(Tier.ELITE);
        assertThat(Tier.ELITE.threshold()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(Tier.ELITE.downgradeLine()).isEqualByComparingTo(new BigDecimal("12000000"));
        assertThat(MemberRankTable.tierOf("M")).isEqualTo(Tier.SUMMIT);
        assertThat(Tier.SUMMIT.threshold()).isEqualByComparingTo(new BigDecimal("30000000"));
        assertThat(Tier.SUMMIT.downgradeLine()).isEqualByComparingTo(new BigDecimal("33000000"));
        for (String code : new String[] {"V", "K"}) {
            assertThat(MemberRankTable.tierOf(code)).isEqualTo(Tier.PRIVATE);
        }
        assertThat(Tier.PRIVATE.threshold()).isNull();
        assertThat(Tier.PRIVATE.downgradeLine()).isNull();
        for (String code : new String[] {"R", "W", "F", "G", "H", "I", "J"}) {
            assertThat(MemberRankTable.tierOf(code)).as("code %s", code).isEqualTo(Tier.RELATIONSHIP);
        }
    }

    @Test
    void nextTier() {
        assertThat(Tier.NONE.next()).isEqualTo(Tier.NEW_RICH);
        assertThat(Tier.NEW_RICH.next()).isEqualTo(Tier.ELITE);
        assertThat(Tier.SUMMIT.next()).isEqualTo(Tier.PRIVATE);
        assertThat(Tier.PRIVATE.next()).isNull();
        assertThat(Tier.RELATIONSHIP.next()).isNull();
    }

    @Test
    void displayNamesAndBenefits() {
        assertThat(Tier.NEW_RICH.displayName()).isEqualTo("新富");
        assertThat(Tier.PRIVATE.displayName()).isEqualTo("極致／私銀");
        assertThat(Tier.NONE.benefits()).isEmpty();
        for (Tier t : new Tier[] {Tier.NEW_RICH, Tier.ELITE, Tier.SUMMIT, Tier.PRIVATE}) {
            assertThat(t.benefits()).as(t.name()).hasSizeGreaterThanOrEqualTo(3);
        }
    }
}
