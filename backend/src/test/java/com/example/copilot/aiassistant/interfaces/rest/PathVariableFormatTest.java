package com.example.copilot.aiassistant.interfaces.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Pins the 400 + field name contract of malformed path variables, independent of where the check lives. */
class PathVariableFormatTest extends IntegrationTestBase {

    private static final String ADMIN = "EEE22222";

    private static void assertInvalid(ResultActions r, String field) throws Exception {
        r.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value(field));
    }

    @Test
    void lowercaseCertNoIs400WithField() throws Exception {
        assertInvalid(mockMvc.perform(post("/api/ai-assistant/customers/{id}/chat", "a123456789")
                .with(TestUsers.as("ICRM00028")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}")), "certNo");
    }

    @Test
    void uppercaseConfigKeyIs400WithField() throws Exception {
        assertInvalid(mockMvc.perform(get("/api/ai-assistant/admin/configs/{key}/versions", "Instruction.X")
                .with(TestUsers.as(ADMIN))), "configKey");
    }

    @Test
    void shortTraceIdIs400WithField() throws Exception {
        assertInvalid(mockMvc.perform(put("/api/ai-assistant/chat/{id}/feedback", "abc")
                .with(TestUsers.as("ICRM00028")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"UP\"}")), "traceId");
    }
}
