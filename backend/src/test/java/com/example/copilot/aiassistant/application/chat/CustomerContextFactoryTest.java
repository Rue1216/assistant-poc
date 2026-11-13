package com.example.copilot.aiassistant.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.application.port.CustomerDataPort;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.user.UserContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CustomerContextFactoryTest extends IntegrationTestBase {

    private static final UserContext AO = new UserContext("ICRM00040", "AO經辦", "AO", Set.of());
    private static final UserContext ADMIN = new UserContext("EEE22222", "IT 管理人員", "ADMIN", Set.of());

    @Autowired
    CustomerContextFactory factory;

    @Autowired
    CustomerDataPort dataPort;

    @Test
    void certNoReturnedByThePortIsNotRevalidated() {
        CustomerDataPort oddFormat = org.mockito.Mockito.mock(CustomerDataPort.class);
        org.mockito.Mockito.when(oddFormat.findCustomer(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(new CustomerRecord("a-123", "王小明", null, null, null, null,
                null, null, null, null, null, null, null, null, null)));
        CustomerContextFactory f = new CustomerContextFactory((user, certNo) -> AccessLevel.FULL, oddFormat);

        CustomerContext ctx = f.create(ADMIN, new CertNo(TestCustomers.RETIREE)).orElseThrow();

        assertThat(ctx.certNo().value()).isEqualTo(TestCustomers.RETIREE);
        assertThat(ctx.selfReferences()).contains("a-123");
        assertThat(f.forEvaluation(new CertNo(TestCustomers.RETIREE), AccessLevel.FULL)).isPresent();
    }

    @Test
    void noneAccessYieldsEmpty() {
        assertThat(factory.create(AO, new CertNo(TestCustomers.COMPLAINT))).isEmpty();
    }

    @Test
    void unknownCustomerYieldsEmpty() {
        assertThat(factory.create(ADMIN, new CertNo(TestCustomers.UNKNOWN))).isEmpty();
    }

    @Test
    void forEvaluationSkipsAccessCheck() {
        CustomerContextFactory denyAll = new CustomerContextFactory((user, certNo) -> AccessLevel.NONE, dataPort);

        CustomerContext ctx = denyAll.forEvaluation(new CertNo(TestCustomers.RETIREE), AccessLevel.FULL).orElseThrow();

        assertThat(ctx.accessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(ctx.selfReferences()).contains("王小明", TestCustomers.RETIREE);
        assertThat(denyAll.forEvaluation(new CertNo(TestCustomers.UNKNOWN), AccessLevel.FULL)).isEmpty();
    }

    @Test
    void limitedAccessCarriesLevel() {
        CustomerContextFactory limited = new CustomerContextFactory((user, certNo) -> AccessLevel.LIMITED, dataPort);

        CustomerContext ctx = limited.create(ADMIN, new CertNo(TestCustomers.RETIREE)).orElseThrow();
        assertThat(ctx.accessLevel()).isEqualTo(AccessLevel.LIMITED);
        assertThat(ctx.selfReferences()).contains("王小明", TestCustomers.RETIREE);
        assertThat(ctx.sensitiveValues()).contains("0912-000-111",
                "xiaoming.wang@example.com", "0123456789012");
    }
}
