package com.example.copilot.mockplatform.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.port.CustomerRecord;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockCustomerDataEnumTest {

    private final MockCustomerData data = new MockCustomerData();

    @Test
    void holdingTypesAndCampaignResponsesStayWithinSpecEnums() {
        for (CustomerRecord c : data.customers()) {
            assertThat(data.holdings(c.customerId())).isNotEmpty()
                    .allSatisfy(h -> assertThat(h.productType())
                            .isIn(Set.of("STOCK", "FUND", "DEPOSIT", "INSURANCE")));
            assertThat(data.campaigns(c.customerId())).isNotEmpty()
                    .allSatisfy(k -> assertThat(k.response())
                            .isIn(Set.of("NO_RESPONSE", "OPENED", "CLICKED", "CONVERTED")));
        }
    }
}
