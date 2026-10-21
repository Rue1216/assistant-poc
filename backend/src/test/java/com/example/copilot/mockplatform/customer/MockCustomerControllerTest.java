package com.example.copilot.mockplatform.customer;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestUsers;
import org.junit.jupiter.api.Test;

class MockCustomerControllerTest extends IntegrationTestBase {

    @Test
    void listShowsOnlyAccessibleCustomers() throws Exception {
        // 02 section 1.4: ao.officer sees C001, C004, C006 (C003 is NOT visible to AO officers).
        mockMvc.perform(get("/api/mock/customers").with(TestUsers.as("ao.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customerId", contains("C001", "C004", "C006")));
    }

    @Test
    void listMasksNamesForLimitedUser() throws Exception {
        mockMvc.perform(get("/api/mock/customers").with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("王○明"))
                .andExpect(jsonPath("$[0].accessLevel").value("LIMITED"));
    }

    @Test
    void detailOfInaccessibleCustomerIs404() throws Exception {
        mockMvc.perform(get("/api/mock/customers/C002").with(TestUsers.as("ao.officer")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CUSTOMER_NOT_FOUND"));
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        mockMvc.perform(get("/api/mock/customers/C999").with(TestUsers.as("hq.viewer")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullUserSeesFictionalIdentity() throws Exception {
        mockMvc.perform(get("/api/mock/customers/C001").with(TestUsers.as("fc.officer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLevel").value("FULL"))
                .andExpect(jsonPath("$.name").value("王小明"))
                .andExpect(jsonPath("$.nationalId").value("A123456789"));
    }

    @Test
    void limitedUserSeesMaskedDetail() throws Exception {
        mockMvc.perform(get("/api/mock/customers/C001").with(TestUsers.as("it.admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLevel").value("LIMITED"))
                .andExpect(jsonPath("$.name").value("王○明"))
                .andExpect(jsonPath("$.nationalId").doesNotExist())
                .andExpect(jsonPath("$.mobile").doesNotExist())
                .andExpect(jsonPath("$.interactions").doesNotExist());
    }
}
