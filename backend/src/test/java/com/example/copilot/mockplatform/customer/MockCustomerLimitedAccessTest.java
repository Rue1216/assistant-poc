package com.example.copilot.mockplatform.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.TestUsers;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** No mock account is LIMITED any more, so the de-identified path is exercised with a stubbed access adapter. */
class MockCustomerLimitedAccessTest extends IntegrationTestBase {

    private static final String USER = "ICRM00047";

    @MockitoBean
    MockCustomerAccessAdapter access;

    @BeforeEach
    void everyoneIsLimited() {
        Mockito.when(access.scopeLevel(Mockito.any(), Mockito.anyString())).thenReturn(AccessLevel.LIMITED);
        Mockito.when(access.checkAccess(Mockito.any(), Mockito.anyString())).thenReturn(AccessLevel.LIMITED);
    }

    @Test
    void listMasksNamesForLimitedUser() throws Exception {
        mockMvc.perform(get("/api/mock/customers").with(TestUsers.as(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].name").value("王○明"))
                .andExpect(jsonPath("$[0].accessLevel").value("LIMITED"));
    }

    @Test
    void limitedCertNoIsMasked() throws Exception {
        mockMvc.perform(get("/api/mock/customers").with(TestUsers.as(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].certNo").value("A*****6789"));
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE).with(TestUsers.as(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certNo").value("A*****6789"));
    }

    @Test
    void limitedUserSeesMaskedDetail() throws Exception {
        mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE).with(TestUsers.as(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLevel").value("LIMITED"))
                .andExpect(jsonPath("$.name").value("王○明"))
                .andExpect(jsonPath("$.certNo").value("A*****6789"))
                .andExpect(jsonPath("$.mobile").doesNotExist());
    }

    @Test
    void limitedUserIsForbiddenFrom360AndRealTime() throws Exception {
        for (String suffix : new String[] {"/360", "/real-time"}) {
            mockMvc.perform(get("/api/mock/customers/" + TestCustomers.RETIREE + suffix).with(TestUsers.as(USER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AI_FORBIDDEN"));
        }
    }

    @Test
    void suggestionsAreTheSameForLimitedAndFull() throws Exception {
        String url = "/api/ai-assistant/customers/{id}/suggestions";
        String limited = mockMvc.perform(get(url, TestCustomers.RETIREE).with(TestUsers.as(USER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Mockito.when(access.checkAccess(Mockito.any(), Mockito.anyString())).thenReturn(AccessLevel.FULL);
        String full = mockMvc.perform(get(url, TestCustomers.RETIREE).with(TestUsers.as(USER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(full, limited);
    }
}
