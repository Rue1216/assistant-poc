package com.example.copilot.aiassistant.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.testsupport.LogCapture;
import com.example.copilot.testsupport.MockCustomerPorts;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomerNotesTest {

    private final CustomerNotes notes = new CustomerNotes(MockCustomerPorts.adapter());

    private static CustomerContext full(String certNo) {
        return new CustomerContext(certNo, AccessLevel.FULL, Set.of(), Set.of());
    }

    @Test
    void customerWithoutNotes() {
        assertThat(notes.section(full(TestCustomers.RETIREE))).isEqualTo("# 本顧客註記\n- 無");
    }

    @Test
    void noMarketingListsTheExpiryDate() {
        assertThat(notes.section(full(TestCustomers.NO_MARKETING)))
                .isEqualTo("# 本顧客註記\n- 不宜行銷（到期日 2027/03/31）");
    }

    @Test
    void complaint() {
        assertThat(notes.section(full(TestCustomers.COMPLAINT))).contains("\n- 客訴");
    }

    @Test
    void specialCare() {
        assertThat(notes.section(full(TestCustomers.SPECIAL_CARE))).contains("\n- 特殊客群");
    }

    @Test
    void attention() {
        assertThat(notes.section(full(TestCustomers.ATTENTION))).contains("\n- 應注意名單");
    }

    @Test
    void highAssetIsNotListed() {
        assertThat(notes.section(full(TestCustomers.HIGH_ASSET))).isEqualTo("# 本顧客註記\n- 無");
    }

    @Test
    void limitedHasNoSection() {
        assertThat(notes.section(new CustomerContext(TestCustomers.NO_MARKETING, AccessLevel.LIMITED, Set.of(),
                Set.of()))).isNull();
    }

    @Test
    void missingRealTimeStatusReadsAsNone() {
        assertThat(notes.section(full(TestCustomers.UNKNOWN))).isEqualTo("# 本顧客註記\n- 無");
    }

    @Test
    void portFailureGivesTheFailureTextAndAWarningWithoutCustomerData() {
        CustomerDataPort failing = Mockito.mock(CustomerDataPort.class);
        Mockito.when(failing.findRealTimeStatus(Mockito.anyString()))
                .thenThrow(new RuntimeException("boom " + TestCustomers.NO_MARKETING));
        try (LogCapture capture = LogCapture.of(CustomerNotes.class, Level.WARN)) {
            String section = new CustomerNotes(failing).section(full(TestCustomers.NO_MARKETING));

            assertThat(section).isEqualTo("# 本顧客註記\n- 無法取得註記，涉及商品或行銷的回答請先至 360 頁面確認註記");
            assertThat(capture.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                String rendered = LogCapture.render(event);
                assertThat(rendered).contains("customer notes unavailable")
                        .doesNotContain(TestCustomers.NO_MARKETING, "boom");
            });
        }
    }
}
