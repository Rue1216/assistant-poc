package com.example.copilot.aiassistant.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.UserContext;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CustomerContextFactoryTest extends IntegrationTestBase {

    private static final UserContext AO = new UserContext("ao.officer", "AO 經辦", "AO_OFFICER", Set.of());
    private static final UserContext ADMIN = new UserContext("it.admin", "IT 管理人員", "IT_ADMIN", Set.of());

    @Autowired
    CustomerContextFactory factory;

    @Autowired
    CustomerDataPort dataPort;

    @Test
    void noneAccessYieldsEmpty() {
        assertThat(factory.create(AO, "C002")).isEmpty();
    }

    @Test
    void unknownCustomerYieldsEmpty() {
        assertThat(factory.create(ADMIN, "C999")).isEmpty();
    }

    @Test
    void forEvaluationSkipsAccessCheck() {
        CustomerContextFactory denyAll = new CustomerContextFactory((user, customerId) -> AccessLevel.NONE, dataPort);

        CustomerContext ctx = denyAll.forEvaluation("C001", AccessLevel.FULL).orElseThrow();

        assertThat(ctx.accessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(ctx.selfReferences()).contains("王小明");
        assertThat(ctx.sensitiveValues()).contains("A123456789");
        assertThat(denyAll.forEvaluation("C999", AccessLevel.FULL)).isEmpty();
    }

    @Test
    void limitedAccessCarriesLevel() {
        CustomerContext ctx = factory.create(ADMIN, "C001").orElseThrow();
        assertThat(ctx.accessLevel()).isEqualTo(AccessLevel.LIMITED);
        assertThat(ctx.selfReferences()).contains("王小明", "C001");
        assertThat(ctx.sensitiveValues()).contains("A123456789", "0912-000-111",
                "xiaoming.wang@example.com", "0123456789012");
    }
}
