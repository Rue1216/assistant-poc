package com.example.copilot.aiassistant.answer;

import static com.example.copilot.aiassistant.answer.NumberChecker.unverified;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NumberCheckerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode data(String json) {
        return MAPPER.readTree(json);
    }

    @Test
    void thousandsSeparatorAndTrailingZeros() {
        assertThat(unverified(List.of("收盤 2,420.00 元，本益比 28.050"),
                List.of(data("{\"close\":2420,\"pe\":\"28.05\"}")))).isEmpty();
    }

    @Test
    void percentComparesValueOnly() {
        assertThat(unverified(List.of("殖利率 0.91%"), List.of(data("{\"dividendYield\":0.91}")))).isEmpty();
    }

    @Test
    void datesCompareMonthAndDay() {
        List<JsonNode> d = List.of(data("{\"dataDate\":\"2026-10-21\"}"));
        assertThat(unverified(List.of("資料日期 2026-10-21、10/21、2026/10/21"), d)).isEmpty();
        assertThat(unverified(List.of("10/22 收盤"), d)).containsExactly("10/22");
    }

    @Test
    void dateFieldsSplitIntoYearMonthDay() {
        assertThat(unverified(List.of("2026 年"), List.of(data("{\"dataDate\":\"2026-10-21\"}")))).isEmpty();
    }

    @Test
    void numbersInsideToolStringsCount() {
        assertThat(unverified(List.of("占比 20%-30%"), List.of(data("{\"weightRange\":\"20%-30%\"}")))).isEmpty();
    }

    @Test
    void smallIntegersAreSkipped() {
        assertThat(unverified(List.of("共 3 筆，第 10 名，11 檔"), List.of())).containsExactly("11");
    }

    @Test
    void signsAreIgnored() {
        assertThat(unverified(List.of("下跌 -20 元"), List.of(data("{\"change\":-20}")))).isEmpty();
    }

    @Test
    void dedupesAndCapsAtTwenty() {
        String text = IntStream.rangeClosed(11, 40).mapToObj(String::valueOf).collect(joining("、")) + "、11";
        assertThat(unverified(List.of(text), List.of())).hasSize(20).startsWith("11", "12").doesNotHaveDuplicates();
    }
}
