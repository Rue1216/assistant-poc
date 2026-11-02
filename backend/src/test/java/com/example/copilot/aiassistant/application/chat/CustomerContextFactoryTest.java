package com.example.copilot.aiassistant.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.application.port.CustomerDataPort;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.user.UserContext;
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
    void noneAccessYieldsEmpty() {
        assertThat(factory.create(AO, TestCustomers.COMPLAINT)).isEmpty();
    }

    @Test
    void unknownCustomerYieldsEmpty() {
        assertThat(factory.create(ADMIN, TestCustomers.UNKNOWN)).isEmpty();
    }

    @Test
    void forEvaluationSkipsAccessCheck() {
        CustomerContextFactory denyAll = new CustomerContextFactory((user, certNo) -> AccessLevel.NONE, dataPort);

        CustomerContext ctx = denyAll.forEvaluation(TestCustomers.RETIREE, AccessLevel.FULL).orElseThrow();

        assertThat(ctx.accessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(ctx.selfReferences()).contains("王小明", TestCustomers.RETIREE);
        assertThat(denyAll.forEvaluation(TestCustomers.UNKNOWN, AccessLevel.FULL)).isEmpty();
    }

    @Test
    void limitedAccessCarriesLevel() {
        CustomerContextFactory limited = new CustomerContextFactory((user, certNo) -> AccessLevel.LIMITED, dataPort);

        CustomerContext ctx = limited.create(ADMIN, TestCustomers.RETIREE).orElseThrow();
        assertThat(ctx.accessLevel()).isEqualTo(AccessLevel.LIMITED);
        assertThat(ctx.selfReferences()).contains("王小明", TestCustomers.RETIREE);
        assertThat(ctx.sensitiveValues()).contains("0912-000-111",
                "xiaoming.wang@example.com", "0123456789012");
    }
}
