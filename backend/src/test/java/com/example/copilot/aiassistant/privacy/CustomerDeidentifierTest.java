package com.example.copilot.aiassistant.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CustomerDeidentifierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void ageRangeBandsAndBirthdayBoundary() {
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(2008, 10, 20), TODAY)).isEqualTo("18-24");
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(2001, 10, 21), TODAY)).isEqualTo("18-24");
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(2001, 10, 20), TODAY)).isEqualTo("25-34");
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(1991, 10, 20), TODAY)).isEqualTo("35-44");
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(1981, 10, 20), TODAY)).isEqualTo("45-54");
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(1968, 3, 14), TODAY)).isEqualTo("55-64");
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(1961, 10, 21), TODAY)).isEqualTo("55-64");
        assertThat(CustomerDeidentifier.ageRange(LocalDate.of(1961, 10, 20), TODAY)).isEqualTo("65+");
    }

    @Test
    void assetTierBoundaries() {
        assertThat(CustomerDeidentifier.assetTier(999_999)).isEqualTo("100萬以下");
        assertThat(CustomerDeidentifier.assetTier(1_000_000)).isEqualTo("100萬-300萬");
        assertThat(CustomerDeidentifier.assetTier(2_999_999)).isEqualTo("100萬-300萬");
        assertThat(CustomerDeidentifier.assetTier(3_000_000)).isEqualTo("300萬-500萬");
        assertThat(CustomerDeidentifier.assetTier(5_000_000)).isEqualTo("500萬-1000萬");
        assertThat(CustomerDeidentifier.assetTier(7_500_000)).isEqualTo("500萬-1000萬");
        assertThat(CustomerDeidentifier.assetTier(10_000_000)).isEqualTo("1000萬以上");
    }

    @Test
    void weightRangeUsesTenPercentBands() {
        assertThat(CustomerDeidentifier.weightRange(new BigDecimal("0.25"))).isEqualTo("20%-30%");
        assertThat(CustomerDeidentifier.weightRange(new BigDecimal("0.30"))).isEqualTo("30%-40%");
        assertThat(CustomerDeidentifier.weightRange(new BigDecimal("0.05"))).isEqualTo("0%-10%");
        assertThat(CustomerDeidentifier.weightRange(new BigDecimal("1"))).isEqualTo("90%-100%");
    }
}
