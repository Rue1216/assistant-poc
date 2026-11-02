package com.example.copilot.aiassistant.interfaces.rest.chat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.TestUsers;
import org.junit.jupiter.api.Test;

class SuggestionControllerTest extends IntegrationTestBase {

    private static final String URL = "/api/ai-assistant/customers/{id}/suggestions";

    @Test
    void suggestionsAreTheSameForEveryFullUser() throws Exception {
        for (String username : new String[] {"EEE22222", "ICRM00028"}) {
            mockMvc.perform(get(URL, TestCustomers.RETIREE).with(TestUsers.as(username)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories.length()").value(7))
                    .andExpect(jsonPath("$.categories[0].configKey").value("instruction.asset-liability"))
                    .andExpect(jsonPath("$.categories[0].name").value("資產負債"))
                    .andExpect(jsonPath("$.categories[0].questions[0]").isNotEmpty());
        }
    }

    @Test
    void suggestionsForStopUseCustomerAre404() throws Exception {
        mockMvc.perform(get(URL, TestCustomers.STOP_USE).with(TestUsers.as("ICRM00028")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CUSTOMER_NOT_FOUND"));
    }

    @Test
    void suggestionsFor404Customer() throws Exception {
        mockMvc.perform(get(URL, TestCustomers.COMPLAINT).with(TestUsers.as("ICRM00040")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CUSTOMER_NOT_FOUND"));
    }

    @Test
    void suggestionsRequireAssistantUse() throws Exception {
        mockMvc.perform(get(URL, TestCustomers.RETIREE).with(user("nobody").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("CUSTOMER_360_VIEW"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_FORBIDDEN"));
    }

    @Test
    void malformedCertNoIs400() throws Exception {
        mockMvc.perform(get(URL, "X1").with(TestUsers.as("ICRM00028")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get(URL, TestCustomers.RETIREE)).andExpect(status().isUnauthorized());
    }
}
