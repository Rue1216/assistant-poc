package com.example.copilot.aiassistant.tool.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TwseParsingTest {

    @Test
    void convertsRocDate() {
        assertThat(TwseParsing.rocDate("1151016")).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(TwseParsing.rocDate("")).isNull();
        assertThat(TwseParsing.rocDate(null)).isNull();
        assertThat(TwseParsing.rocDate("1151345")).isNull();
        assertThat(TwseParsing.rocDate("abc")).isNull();
    }

    @Test
    void parsesNumbers() {
        assertThat(TwseParsing.number("2,420.00")).isEqualByComparingTo(new BigDecimal("2420.00"));
        assertThat(TwseParsing.number("-20.0000")).isEqualByComparingTo(new BigDecimal("-20"));
        assertThat(TwseParsing.number("--")).isNull();
        assertThat(TwseParsing.number("")).isNull();
        assertThat(TwseParsing.number(null)).isNull();
    }

    @Test
    void formatsTime() {
        assertThat(TwseParsing.time("70003")).isEqualTo("07:00:03");
        assertThat(TwseParsing.time("093015")).isEqualTo("09:30:15");
        assertThat(TwseParsing.time("")).isNull();
        assertThat(TwseParsing.time("996699")).isNull();
    }
}
