package com.example.copilot.mockplatform.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockCustomerDataEnumTest {

    private final MockCustomerData data = new MockCustomerData();

    @Test
    void segmentsAndBranchesStayWithinTheMockSets() {
        Set<String> branches = Set.of("B101", "B102", "B201", "B301");
        for (CustomerRecord c : data.customers()) {
            assertThat(c.segment()).matches("[A-Z]\\d{2}-.+");
            assertThat(List.of(c.primaryBranch(), c.profitBranch(), c.wealthBranch(), c.assigneeBranch()))
                    .isSubsetOf(branches);
        }
    }

    @Test
    void onlyTheStopUseCustomerCarriesTheFlag() {
        assertThat(data.customers()).hasSize(7);
        for (CustomerRecord c : data.customers()) {
            assertThat(data.stopUseCustomerData(c.certNo())).as(c.name())
                    .isEqualTo(c.certNo().equals(TestCustomers.STOP_USE));
        }
    }
}
