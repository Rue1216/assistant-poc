package com.example.copilot.aiassistant.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.application.chat.CustomerContextFactory;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();
    private final CustomerContext retiree = new CustomerContext(TestCustomers.RETIREE, AccessLevel.FULL,
            Set.of(TestCustomers.RETIREE, "王小明"),
            Set.of("0912-000-111", "xiaoming.wang@example.com", "0123456789012"));

    @Test
    void replacesSelfName() {
        assertThat(redactor.redact("王小明最近有什麼互動？", retiree)).isEqualTo("【本顧客】最近有什麼互動？");
    }

    @Test
    void replacesSelfCertNo() {
        assertThat(redactor.redact(TestCustomers.RETIREE + " 的概況", retiree)).isEqualTo("【本顧客】 的概況");
    }

    @Test
    void masksKnownSensitiveValues() {
        assertThat(redactor.redact("王小明的手機 0912-000-111 最近有聯絡過嗎？", retiree))
                .isEqualTo("【本顧客】的手機 【已遮蔽】 最近有聯絡過嗎？");
        assertThat(redactor.redact("信箱 xiaoming.wang@example.com 帳號 0123456789012", retiree))
                .isEqualTo("信箱 【已遮蔽】 帳號 【已遮蔽】");
    }

    @Test
    void masksKnownValuesWrittenWithoutSeparators() {
        assertThat(redactor.redact("電話 0912000111", retiree)).isEqualTo("電話 【已遮蔽】");
    }

    @Test
    void masksPatternMatches() {
        assertThat(redactor.redact("幫我查 XB223456789", retiree)).isEqualTo("幫我查 X【已遮蔽】");
        assertThat(redactor.redact("0933-222-444", retiree)).isEqualTo("【已遮蔽】");
        assertThat(redactor.redact("a@b.co", retiree)).isEqualTo("【已遮蔽】");
        assertThat(redactor.redact("帳號 9876543210", retiree)).isEqualTo("帳號 【已遮蔽】");
    }

    @Test
    void otherCertNos() {
        assertThat(redactor.redact("本顧客 " + TestCustomers.RETIREE, retiree)).isEqualTo("本顧客 【本顧客】");
        assertThat(redactor.redact("比較 " + TestCustomers.COMPLAINT + " 的持股", retiree))
                .isEqualTo("比較 【其他顧客】 的持股");
        assertThat(redactor.redact("居留證 HKC123456", retiree)).isEqualTo("居留證 【其他顧客】");
        assertThat(redactor.redact("統編 &F12345678", retiree)).isEqualTo("統編 【其他顧客】");
        assertThat(redactor.redact("舊編號 C123", retiree)).isEqualTo("舊編號 C123");
    }

    @Test
    void leavesStockCodesAlone() {
        assertThat(redactor.redact("2330 收盤價", retiree)).isEqualTo("2330 收盤價");
    }

    @Test
    void redactsFullWidthAndLowercaseVariants() {
        assertThat(redactor.redact("０９１２－０００－１１１", retiree)).contains("【已遮蔽】").doesNotContain("0912");
        assertThat(redactor.redact(TestCustomers.COMPLAINT.toLowerCase(), retiree)).contains("【其他顧客】").doesNotContain("223456780");
    }

    @Test
    void masksKnownMobileWithSeparatorVariants() {
        String[] variants = {
            "0912 - 000 - 111", "0912  000  111", "0912–000–111", "0912—000—111", "0912‐000‐111",
            "0912−000−111", "0912.000.111", "(0912)000111", "+886-912-000-111", "+886 912 000 111",
            "0912​000111", "0912­000111"
        };
        for (String v : variants) {
            assertThat(redactor.redact("電話 " + v + " 謝謝", retiree)).as(v).contains("【已遮蔽】").doesNotContain("912").doesNotContain("000");
        }
    }

    @Test
    void foldsCompatibilityCharactersInName() {
        assertThat(redactor.redact("王⼩明最近有什麼互動？", retiree)).isEqualTo("【本顧客】最近有什麼互動？");
    }

    @Test
    void skipsDegenerateKnownValues() {
        CustomerContext odd = new CustomerContext(TestCustomers.RETIREE, AccessLevel.FULL, Set.of("王小明"), Set.of("-", " "));
        assertThat(redactor.redact("2330 收盤價 12-34", odd)).isEqualTo("2330 收盤價 12-34");
    }

    @Test
    void toleratesWhitespaceInsideKnownNameAndCertNo() {
        assertThat(redactor.redact("王 小 明", retiree)).isEqualTo("【本顧客】");
        assertThat(redactor.redact("A 123456789", retiree)).isEqualTo("【本顧客】");
    }

    @Test
    void masksIdGluedToLettersAndLongDigitRuns() {
        assertThat(redactor.redact("idB223456789", retiree)).isEqualTo("id【已遮蔽】");
        assertThat(redactor.redact("帳號 12345678901234567890", retiree)).isEqualTo("帳號 【已遮蔽】");
    }

    @Test
    void rejectsNullCustomer() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> redactor.redact("x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullStaysNull() {
        assertThat(redactor.redact(null, retiree)).isNull();
    }

    @Test
    void contextToStringHidesIdentifyingValues() {
        assertThat(retiree.toString()).isEqualTo("CustomerContext[accessLevel=FULL]");
    }

    private static CustomerContext contextFor(String name) {
        return CustomerContextFactory.build(new CustomerRecord(TestCustomers.RETIREE, name, "0912-000-111",
                "xiaoming.wang@example.com", List.of("0123456789012"), null, null, null, null, null,
                null, null, null, null, null), AccessLevel.FULL);
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
