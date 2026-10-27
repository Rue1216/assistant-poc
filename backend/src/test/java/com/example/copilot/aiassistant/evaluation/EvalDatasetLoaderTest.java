package com.example.copilot.aiassistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.aiassistant.port.AccessLevel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvalDatasetLoaderTest {

    private static final Set<String> TOOLS = Set.of("getCustomerProfile", "getCustomerHoldings",
            "getRecentInteractions", "getCampaignResults", "getStockQuote", "getCompanyAnnouncements",
            "getMarketIndex");

    private static EvalDataset parse(String yaml, int maxCases) {
        return EvalDatasetLoader.parse(yaml.getBytes(StandardCharsets.UTF_8), TOOLS, maxCases);
    }

    private static String valid(String id) {
        return """
                - id: %s
                  group: 顧客經營
                  accessLevel: FULL
                  customerId: C001
                  question: 整理近期互動
                  expect:
                    tools: [getRecentInteractions]
                    refusal: false
                  rubric:
                    - 列出近期互動
                """.formatted(id);
    }

    @Test
    void parsesValidCase() {
        EvalDataset dataset = parse(valid("Q3-contact"), 30);

        assertThat(dataset.hash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(dataset.cases()).hasSize(1);
        EvalCase c = dataset.cases().get(0);
        assertThat(c.id()).isEqualTo("Q3-contact");
        assertThat(c.group()).isEqualTo("顧客經營");
        assertThat(c.accessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(c.customerId()).isEqualTo("C001");
        assertThat(c.question()).isEqualTo("整理近期互動");
        assertThat(c.expect().tools()).containsExactly("getRecentInteractions");
        assertThat(c.expect().forbiddenTools()).isEmpty();
        assertThat(c.expect().refusal()).isFalse();
        assertThat(c.expect().mustContain()).isEmpty();
        assertThat(c.expect().mustNotContain()).isEmpty();
        assertThat(c.rubric()).containsExactly("列出近期互動");
    }

    @Test
    void reportsAllErrorsAtOnce() {
        String yaml = valid("Q1") + valid("Q1") + """
                - id: Q2
                  group: 顧客經營
                  accessLevel: FULL
                  customerId: C001
                  question: ok
                  expect:
                    tools: [deleteCustomer, getStockQuote]
                    forbiddenTools: [getStockQuote]
                    refusal: false
                  rubric: []
                - id: Q3
                  group: 顧客經營
                  accessLevel: FULL
                  customerId: C001
                  question: "%s"
                  expect:
                    refusal: false
                  rubric: [ok]
                """.formatted("x".repeat(1001));

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                .message()
                .contains("Q1.id")
                .contains("Q2.expect.tools")
                .contains("deleteCustomer")
                .contains("Q2.expect.forbiddenTools")
                .contains("Q2.rubric")
                .contains("Q3.question");
    }

    @Test
    void rejectsTooManyCases() {
        String yaml = valid("A1") + valid("A2") + valid("A3");

        assertThatThrownBy(() -> parse(yaml, 2)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("最多 2 題");
    }

    @Test
    void rejectsUnsafeYaml() {
        String yaml = "- !!java.io.File [/tmp]\n";

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBadEnumsAndLengths() {
        String items = java.util.stream.IntStream.rangeClosed(1, 11).mapToObj(i -> "m" + i)
                .collect(java.util.stream.Collectors.joining(", "));
        String yaml = """
                - id: "Q 1"
                  group: "%s"
                  accessLevel: NONE
                  customerId: C001
                  question: ok
                  expect:
                    refusal: false
                    mustContain: [%s]
                    mustNotContain: ["%s"]
                  rubric: [ok]
                """.formatted("g".repeat(21), items, "y".repeat(51));

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                .message()
                .contains("#1.id")
                .contains("#1.group")
                .contains("#1.accessLevel")
                .contains("#1.expect.mustContain")
                .contains("#1.expect.mustNotContain");
    }

    @Test
    void rejectsUnknownCaseKey() {
        String yaml = valid("Q1") + """
                - group: 顧客經營
                  accessLevel: FULL
                  customerId: C001
                  question: ok
                  expect:
                    refusal: false
                  rubric: [ok]
                  note: 多的欄位
                """ + valid("Q2").replace("  rubric:", "  rubrik: [x]\n  rubric:");

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                .message()
                .contains("eval-cases.yml: #2.note: 未知的欄位")
                .contains("eval-cases.yml: Q2.rubrik: 未知的欄位")
                .doesNotContain("Q1.");
    }

    @Test
    void rejectsUnknownExpectKey() {
        String yaml = valid("Q1").replace("    refusal: false", "    refusal: false\n    mustNotContian: [保證]");

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eval-cases.yml: Q1.expect.mustNotContian: 未知的欄位");
    }

    @Test
    void acceptsCategoryKeyAndNone() {
        for (String category : List.of("instruction.contact-prep", "none")) {
            String yaml = valid("Q1").replace("    refusal: false", "    refusal: false\n    category: " + category);
            assertThat(parse(yaml, 30).cases().get(0).expect().category()).isEqualTo(category);
        }
        assertThat(parse(valid("Q1"), 30).cases().get(0).expect().category()).isNull();
    }

    @Test
    void rejectsBadCategory() {
        for (String category : List.of("system.main", "Contact", "instruction.Bad", "'123'")) {
            String yaml = valid("Q1").replace("    refusal: false", "    refusal: false\n    category: " + category);
            assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("eval-cases.yml: Q1.expect.category: ");
        }
    }

    @Test
    void rejectsDuplicateKey() {
        String yaml = valid("Q1").replace("  question: 整理近期互動", "  question: 整理近期互動\n  question: 另一題");

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("eval-cases.yml: ")
                .hasMessageContaining("重複的欄位");
    }

    @Test
    void bundledDatasetIsValid() throws IOException {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/eval/eval-cases.yml")) {
            assertThat(in).isNotNull();
            bytes = in.readAllBytes();
        }

        EvalDataset dataset = EvalDatasetLoader.parse(bytes, TOOLS, 30);

        assertThat(dataset.cases()).hasSize(17);
        List<String> refusals = dataset.cases().stream().filter(c -> c.expect().refusal())
                .map(EvalCase::id).toList();
        assertThat(refusals).containsExactly("P5-limited-contact", "D4-identity", "D5-other-customer",
                "X1-system-prompt", "X3-action", "X5-advice-refuse");
        java.util.Map<String, String> categories = new java.util.HashMap<>();
        dataset.cases().forEach(c -> categories.put(c.id(), c.expect().category()));
        assertThat(categories).hasSize(17)
                .containsEntry("Q1-overview", "instruction.customer-overview")
                .containsEntry("P5-limited-profile", "instruction.customer-overview")
                .containsEntry("Q2-opportunity", "instruction.opportunity")
                .containsEntry("Q3-contact-prep", "instruction.contact-prep")
                .containsEntry("P5-limited-contact", "instruction.contact-prep")
                .containsEntry("D1-name", "instruction.contact-prep")
                .containsEntry("X2-indirect", "instruction.contact-prep")
                .containsEntry("Q4-contact-topic", "instruction.contact-topic")
                .containsEntry("K1-index", "instruction.market-brief")
                .containsEntry("K2-quote", "instruction.market-brief")
                .containsEntry("K3-announcement", "instruction.market-brief")
                .containsEntry("X1-system-prompt", "none")
                .containsEntry("X3-action", "none")
                .containsEntry("Q5-opening", null)
                .containsEntry("D4-identity", null)
                .containsEntry("D5-other-customer", null)
                .containsEntry("X5-advice-refuse", null);
    }
}
