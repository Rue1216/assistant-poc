package com.example.copilot.aiassistant.domain.tool.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DisplayFormatTest {

    @Test
    void amountRoundsToTwoDecimalsAndDropsTrailingZeros() {
        assertThat(DisplayFormat.amount(new BigDecimal("1000.345"))).isEqualTo("1,000.35 元");
        assertThat(DisplayFormat.amount(new BigDecimal("12.304"))).isEqualTo("12.3 元");
        assertThat(DisplayFormat.amount(new BigDecimal("12.004"))).isEqualTo("12 元");
    }

    @Test
    void amountFromLongUsesThousandsSeparator() {
        assertThat(DisplayFormat.amount(8_200_000L)).isEqualTo("8,200,000 元");
        assertThat(DisplayFormat.amount(-1500L)).isEqualTo("-1,500 元");
    }

    @Test
    void percentUsesTwoDecimals() {
        assertThat(DisplayFormat.percent(new BigDecimal("25"))).isEqualTo("25.00%");
    }

    @Test
    void percentPointsCarriesSign() {
        assertThat(DisplayFormat.percentPoints(new BigDecimal("16.6"))).isEqualTo("+16.60 個百分點");
        assertThat(DisplayFormat.percentPoints(new BigDecimal("-7.4"))).isEqualTo("-7.40 個百分點");
        assertThat(DisplayFormat.percentPoints(BigDecimal.ZERO)).isEqualTo("0.00 個百分點");
        assertThat(DisplayFormat.percentPoints(new BigDecimal("0.001"))).isEqualTo("0.00 個百分點");
        assertThat(DisplayFormat.percentPoints(new BigDecimal("-0.001"))).isEqualTo("0.00 個百分點");
    }

    @Test
    void rateUsesThreeDecimals() {
        assertThat(DisplayFormat.rate(new BigDecimal("2.045"))).isEqualTo("2.045%");
    }

    @Test
    void ratioUsesTwoDecimalsWithoutUnit() {
        assertThat(DisplayFormat.ratio(new BigDecimal("0.8317"))).isEqualTo("0.83");
    }

    @Test
    void yesNo() {
        assertThat(DisplayFormat.yesNo(true)).isEqualTo("是");
        assertThat(DisplayFormat.yesNo(false)).isEqualTo("否");
    }
}
