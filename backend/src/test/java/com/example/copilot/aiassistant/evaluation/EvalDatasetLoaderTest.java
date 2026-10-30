package com.example.copilot.aiassistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.port.AccessLevel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvalDatasetLoaderTest {

    private static final Set<String> TOOLS = Set.of("getCustomer", "getStockQuote", "getCompanyAnnouncements",
            "getMarketIndex");

    private static EvalDataset parse(String yaml, int maxCases) {
        return EvalDatasetLoader.parse(yaml.getBytes(StandardCharsets.UTF_8), TOOLS, maxCases);
    }

    private static String valid(String id) {
        return """
                - id: %s
                  group: 顧客經營
                  accessLevel: FULL
                  certNo: A123456789
                  question: 整理顧客概況
                  expect:
                    tools: [getCustomer]
                    refusal: false
                  rubric:
                    - 列出顧客概況
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
        assertThat(c.certNo()).isEqualTo(TestCustomers.RETIREE);
        assertThat(c.question()).isEqualTo("整理顧客概況");
        assertThat(c.expect().tools()).containsExactly("getCustomer");
        assertThat(c.expect().forbiddenTools()).isEmpty();
        assertThat(c.expect().refusal()).isFalse();
        assertThat(c.expect().mustContain()).isEmpty();
        assertThat(c.expect().mustNotContain()).isEmpty();
        assertThat(c.rubric()).containsExactly("列出顧客概況");
    }

    @Test
    void reportsAllErrorsAtOnce() {
        String yaml = valid("Q1") + valid("Q1") + """
                - id: Q2
                  group: 顧客經營
                  accessLevel: FULL
                  certNo: A123456789
                  question: ok
                  expect:
                    tools: [deleteCustomer, getStockQuote]
                    forbiddenTools: [getStockQuote]
                    refusal: false
                  rubric: []
                - id: Q3
                  group: 顧客經營
                  accessLevel: FULL
                  certNo: A123456789
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
    void rejectsMalformedCertNo() {
        String yaml = valid("Q1").replace("certNo: A123456789", "certNo: a123456789");

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Q1.certNo");
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
                  certNo: A123456789
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
                  certNo: A123456789
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
        String yaml = valid("Q1").replace("  question: 整理顧客概況", "  question: 整理顧客概況\n  question: 另一題");

        assertThatThrownBy(() -> parse(yaml, 30)).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("eval-cases.yml: ")
                .hasMessageContaining("重複的欄位");
    }

    private static final Set<String> BUNDLED_TOOLS = Set.of("getCustomer", "getRealTimeCustomer",
            "getMarketIndex",
            "getStockQuote", "getCompanyAnnouncements");

    private static final Map<String, String> CUSTOMER_NAMES = Map.of(
            TestCustomers.RETIREE, "王小明", TestCustomers.COMPLAINT, "林美君", TestCustomers.ATTENTION, "陳志華",
            TestCustomers.NO_MARKETING, "黃俊豪", TestCustomers.HIGH_ASSET, "周雅婷", TestCustomers.SPECIAL_CARE, "吳建德");

    /**
     * Rules every bundled case must follow. The dataset's content (which cases, how many per group, which tools)
     * lives only in eval-cases.yml, so adding a case does not touch this test.
     */
    @Test
    void bundledDatasetFollowsRules() throws IOException {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/eval/eval-cases.yml")) {
            assertThat(in).isNotNull();
            bytes = in.readAllBytes();
        }
        List<EvalCase> cases = EvalDatasetLoader.parse(bytes, BUNDLED_TOOLS, EvalConfiguration.STORED_CASES_MAX)
                .cases();

        assertThat(cases).isNotEmpty();
        assertThat(cases.stream().map(EvalCase::id).distinct()).hasSameSizeAs(cases);
        // dual-intent cases measure routing behaviour, so no single category is expected
        assertThat(cases.stream().filter(c -> c.group().equals("雙意圖")))
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.expect().category()).as(c.id()).isNull());
        // the permission group exercises the LIMITED view of the high-asset customer
        assertThat(cases.stream().filter(c -> c.group().equals("權限")))
                .isNotEmpty()
                .allSatisfy(c -> {
                    assertThat(c.accessLevel()).as(c.id()).isEqualTo(AccessLevel.LIMITED);
                    assertThat(c.certNo()).as(c.id()).isEqualTo(TestCustomers.HIGH_ASSET);
                });

        for (EvalCase c : cases) {
            assertThat(c.expect().tools()).isSubsetOf(BUNDLED_TOOLS);
            assertThat(c.expect().forbiddenTools()).isSubsetOf(BUNDLED_TOOLS);
            assertThat(CUSTOMER_NAMES).containsKey(c.certNo());
            assertThat(c.expect().mustNotContain()).as(c.id()).contains(CUSTOMER_NAMES.get(c.certNo()));
            assertThat(c.certNo()).isNotEqualTo(TestCustomers.STOP_USE);
        }
    }
}
