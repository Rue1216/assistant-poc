package com.example.copilot.mockplatform.customer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.TestUsers;
import org.junit.jupiter.api.Test;

class MockCustomerControllerTest extends IntegrationTestBase {

    @Test
    void listShowsOnlyAccessibleCustomers() throws Exception {
        // 02 section 1.4: ao.officer sees the retiree, no-marketing, special-care and stop-use customers (not the
        // attention one); the list follows scope only.
        mockMvc.perform(get("/api/mock/customers").with(TestUsers.as("ao.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].certNo", 
                        contains(TestCustomers.RETIREE, TestCustomers.NO_MARKETING, TestCustomers.SPECIAL_CARE,
                                TestCustomers.STOP_USE)));
    }

    @Test
    void listMasksNamesForLimitedUser() throws Exception {
        mockMvc.perform(get("/api/mock/customers").with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].name").value("王○明"))
                .andExpect(jsonPath("$[0].accessLevel").value("LIMITED"));
    }

    @Test
    void limitedCertNoIsMasked() throws Exception {
        mockMvc.perform(get("/api/mock/customers").with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].certNo").value("A*****6789"));
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE).with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certNo").value("A*****6789"));
    }

    @Test
    void detailOfInaccessibleCustomerIs404() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.COMPLAINT).with(TestUsers.as("ao.officer")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CUSTOMER_NOT_FOUND"));
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.UNKNOWN).with(TestUsers.as("hq.viewer")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullUserSeesFictionalIdentity() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE).with(TestUsers.as("fc.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLevel").value("FULL"))
                .andExpect(jsonPath("$.name").value("王小明"))
                .andExpect(jsonPath("$.certNo").value(TestCustomers.RETIREE))
                .andExpect(jsonPath("$.segment").value("R01-退休樂活客群"))
                .andExpect(jsonPath("$.nationalId").doesNotExist());
    }

    @Test
    void limitedUserSeesMaskedDetail() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE).with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLevel").value("LIMITED"))
                .andExpect(jsonPath("$.name").value("王○明"))
                .andExpect(jsonPath("$.certNo").value("A*****6789"))
                .andExpect(jsonPath("$.mobile").doesNotExist());
    }

    @Test
    void detailHasOnlyFourKeys() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE).with(TestUsers.as("fc.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()").value(containsInAnyOrder("certNo", "accessLevel", "name", "segment")));
    }

    @Test
    void fullUserGetsThe360WithPlatformFieldNames() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE + "/360").with(TestUsers.as("fc.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataDate").value("2026/10/27"))
                .andExpect(jsonPath("$.customerProfile.segement").value("R01-退休樂活客群"))
                .andExpect(jsonPath("$.customerProfile.financialCounsultant").value("16888 李大發"))
                .andExpect(jsonPath("$.memberRank.segementProgressPercent").value("52.0"))
                .andExpect(jsonPath("$.aum.auMData.timeDeposit.amountList[0]").value(3000000))
                .andExpect(jsonPath("$.lum.luMData.mtgPurchase.amountList[0]").value(5200000));
    }

    @Test
    void fullUserGetsTheRealTimeStatus() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE + "/real-time")
                        .with(TestUsers.as("fc.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyAlerts.items.length()").value(3))
                .andExpect(jsonPath("$.attentionCustomer").value(false));
    }

    @Test
    void limitedUserIsForbiddenFrom360AndRealTime() throws Exception {
        for (String suffix : new String[] {"/360", "/real-time"}) {
            mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE + suffix)
                            .with(TestUsers.as("it.admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AI_FORBIDDEN"));
        }
    }

    @Test
    void outOfScopeAndUnknownCustomersAre404For360AndRealTime() throws Exception {
        for (String suffix : new String[] {"/360", "/real-time"}) {
            mockMvc.perform(get("/api/mock/customers/" + TestCustomers.COMPLAINT + suffix)
                            .with(TestUsers.as("ao.officer")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AI_CUSTOMER_NOT_FOUND"));
            mockMvc.perform(get("/api/mock/customers/" + TestCustomers.UNKNOWN + suffix)
                            .with(TestUsers.as("hq.viewer")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AI_CUSTOMER_NOT_FOUND"));
        }
    }

    @Test
    void stopUseCustomerIsBlocked() throws Exception {
        // fc.officer is the assignee, so the scope would be FULL; the stop-use flag hides the customer anyway.
        for (String suffix : new String[] {"", "/360", "/real-time"}) {
            mockMvc.perform(get("/api/mock/customers/" + TestCustomers.STOP_USE + suffix)
                            .with(TestUsers.as("fc.officer")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AI_CUSTOMER_NOT_FOUND"));
        }
    }

    @Test
    void cipReportsTheStopUseFlagByScope() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.STOP_USE + "/cip").with(TestUsers.as("fc.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()").value(containsInAnyOrder("stopUseCustomerDataFlag")))
                .andExpect(jsonPath("$.stopUseCustomerDataFlag").value("Y"));
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE + "/cip").with(TestUsers.as("fc.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopUseCustomerDataFlag").value("N"));
        // Out of scope (a customer in another branch) and unknown are 404.
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.COMPLAINT + "/cip").with(TestUsers.as("ao.officer")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.UNKNOWN + "/cip").with(TestUsers.as("hq.viewer")))
                .andExpect(status().isNotFound());
    }
}
