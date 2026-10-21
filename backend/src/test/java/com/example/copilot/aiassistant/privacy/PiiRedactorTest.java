package com.example.copilot.aiassistant.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerRecord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();
    private final CustomerContext c001 = new CustomerContext("C001", AccessLevel.FULL,
            Set.of("C001", "王小明"),
            Set.of("A123456789", "0912-000-111", "xiaoming.wang@example.com", "0123456789012"));

    @Test
    void replacesSelfName() {
        assertThat(redactor.redact("王小明最近有什麼互動？", c001)).isEqualTo("【本顧客】最近有什麼互動？");
    }

    @Test
    void replacesSelfCustomerId() {
        assertThat(redactor.redact("C001 的概況", c001)).isEqualTo("【本顧客】 的概況");
    }

    @Test
    void masksKnownSensitiveValues() {
        assertThat(redactor.redact("王小明的手機 0912-000-111 最近有聯絡過嗎？", c001))
                .isEqualTo("【本顧客】的手機 【已遮蔽】 最近有聯絡過嗎？");
        assertThat(redactor.redact("信箱 xiaoming.wang@example.com 帳號 0123456789012", c001))
                .isEqualTo("信箱 【已遮蔽】 帳號 【已遮蔽】");
    }

    @Test
    void masksKnownValuesWrittenWithoutSeparators() {
        assertThat(redactor.redact("電話 0912000111", c001)).isEqualTo("電話 【已遮蔽】");
    }

    @Test
    void masksPatternMatches() {
        assertThat(redactor.redact("幫我查 B223456789", c001)).isEqualTo("幫我查 【已遮蔽】");
        assertThat(redactor.redact("0933-222-444", c001)).isEqualTo("【已遮蔽】");
        assertThat(redactor.redact("a@b.co", c001)).isEqualTo("【已遮蔽】");
        assertThat(redactor.redact("帳號 9876543210", c001)).isEqualTo("帳號 【已遮蔽】");
    }

    @Test
    void replacesOtherCustomerIds() {
        assertThat(redactor.redact("比較 C002 的持股", c001)).isEqualTo("比較 【其他顧客】 的持股");
    }

    @Test
    void leavesStockCodesAlone() {
        assertThat(redactor.redact("2330 收盤價", c001)).isEqualTo("2330 收盤價");
    }

    @Test
    void redactsFullWidthAndLowercaseVariants() {
        assertThat(redactor.redact("０９１２－０００－１１１", c001)).contains("【已遮蔽】").doesNotContain("0912");
        assertThat(redactor.redact("a123456789", c001)).contains("【已遮蔽】").doesNotContain("123456789");
    }

    @Test
    void masksKnownMobileWithSeparatorVariants() {
        String[] variants = {
            "0912 - 000 - 111", "0912  000  111", "0912–000–111", "0912—000—111", "0912‐000‐111",
            "0912−000−111", "0912.000.111", "(0912)000111", "+886-912-000-111", "+886 912 000 111",
            "0912​000111", "0912­000111"
        };
        for (String v : variants) {
            assertThat(redactor.redact("電話 " + v + " 謝謝", c001)).as(v).contains("【已遮蔽】").doesNotContain("912").doesNotContain("000");
        }
    }

    @Test
    void foldsCompatibilityCharactersInName() {
        assertThat(redactor.redact("王⼩明最近有什麼互動？", c001)).isEqualTo("【本顧客】最近有什麼互動？");
    }

    @Test
    void skipsDegenerateKnownValues() {
        CustomerContext odd = new CustomerContext("C001", AccessLevel.FULL, Set.of("王小明"), Set.of("-", " "));
        assertThat(redactor.redact("2330 收盤價 12-34", odd)).isEqualTo("2330 收盤價 12-34");
    }

    @Test
    void toleratesWhitespaceInsideKnownNameAndNationalId() {
        assertThat(redactor.redact("王 小 明", c001)).isEqualTo("【本顧客】");
        assertThat(redactor.redact("A 123456789", c001)).isEqualTo("【已遮蔽】");
    }

    @Test
    void masksIdGluedToLettersAndLongDigitRuns() {
        assertThat(redactor.redact("idB223456789", c001)).isEqualTo("id【已遮蔽】");
        assertThat(redactor.redact("帳號 12345678901234567890", c001)).isEqualTo("帳號 【已遮蔽】");
    }

    @Test
    void rejectsNullCustomer() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> redactor.redact("x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullStaysNull() {
        assertThat(redactor.redact(null, c001)).isNull();
    }

    @Test
    void contextToStringHidesIdentifyingValues() {
        assertThat(c001.toString()).isEqualTo("CustomerContext[accessLevel=FULL]");
    }

    private static CustomerContext contextFor(String name) {
        return CustomerContextFactory.build(new CustomerRecord("C001", name, "A123456789", "0912-000-111",
                "xiaoming.wang@example.com", List.of("0123456789012"), null, null, null, null, 0, 0, null, false,
                null, List.of(), null, null, null, null, null, null), AccessLevel.FULL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"先生", "小姐", "女士", "太太", "大哥", "大姐", "大姊", "哥", "姐", "姊"})
    void redactsSurnameWithEachHonorific(String honorific) {
        CustomerContext ctx = contextFor("王小明");
        assertThat(redactor.redact("王" + honorific + "最近好嗎", ctx)).isEqualTo("【本顧客】最近好嗎");
    }

    @Test
    void redactsSurnameWithHonorificAcrossSpaces() {
        CustomerContext ctx = contextFor("王小明");
        assertThat(redactor.redact("王 先生最近好嗎", ctx)).isEqualTo("【本顧客】最近好嗎");
        assertThat(redactor.redact("王　先生最近好嗎", ctx)).isEqualTo("【本顧客】最近好嗎");
    }

    @Test
    void redactsGivenName() {
        assertThat(redactor.redact("小明最近好嗎", contextFor("王小明"))).isEqualTo("【本顧客】最近好嗎");
    }

    @Test
    void redactsMaskedDisplayName() {
        assertThat(redactor.redact("王○明的資料", contextFor("王小明"))).isEqualTo("【本顧客】的資料");
        assertThat(redactor.redact("王○的資料", contextFor("王明"))).isEqualTo("【本顧客】的資料");
    }

    @Test
    void fullNameIsOneToken() {
        assertThat(redactor.redact("王小明", contextFor("王小明"))).isEqualTo("【本顧客】");
    }

    @Test
    void leavesUnrelatedWordsWithSurname() {
        assertThat(redactor.redact("王道銀行、國王", contextFor("王小明"))).isEqualTo("王道銀行、國王");
    }

    @Test
    void twoCharacterNameDoesNotAddSingleCharacterGivenName() {
        CustomerContext ctx = contextFor("王明");
        assertThat(redactor.redact("明天王小姐來訪", ctx)).isEqualTo("明天【本顧客】來訪");
        assertThat(redactor.redact("明天", ctx)).isEqualTo("明天");
    }
}
