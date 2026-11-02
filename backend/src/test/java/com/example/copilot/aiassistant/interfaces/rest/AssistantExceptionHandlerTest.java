package com.example.copilot.aiassistant.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AssistantExceptionHandlerTest extends IntegrationTestBase {

    private ResultActions call(String path, String json) throws Exception {
        return mockMvc.perform(post("/test-support/" + path)
                .with(user("tester")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void mapsAssistantExceptionToApiError() throws Exception {
        MvcResult result = call("conflict", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_CONFLICT"))
                .andExpect(jsonPath("$.message").value("內容已被他人更新，請重新載入後再編輯"))
                .andReturn();
        String headerId = result.getResponse().getHeader("X-Request-Id");
        assertThat(headerId).matches("[0-9a-f]{8}");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"requestId\":\"" + headerId + "\"");
    }

    @Test
    void includesDetails() throws Exception {
        call("details", "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AI_CONFIG_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(1)));
    }

    @Test
    void unknownExceptionBecomesInternalError() throws Exception {
        call("unknown", "{}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("AI_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系統發生錯誤，請稍後再試"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

    @Test
    void validationFailureBecomesInputInvalid() throws Exception {
        call("validate", "{\"message\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("message"));
    }

    @Test
    void accessDeniedBecomesForbidden() throws Exception {
        call("denied", "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("您沒有使用此功能的權限"));
    }

    @Test
    void typeMismatchBecomesInputInvalid() throws Exception {
        mockMvc.perform(get("/test-support/limit").param("limit", "abc").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("limit"));
    }

    @Test
    void missingParameterBecomesInputInvalid() throws Exception {
        mockMvc.perform(get("/test-support/limit").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("limit"));
    }

    @Test
    void invalidPathVariableBecomesInputInvalid() throws Exception {
        mockMvc.perform(get("/test-support/items/bad").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void malformedJsonBecomesInputInvalid() throws Exception {
        call("validate", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_INPUT_INVALID"));
    }

    @Test
    void unauthenticatedResponseStillCarriesRequestId() throws Exception {
        mockMvc.perform(post("/test-support/conflict").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AI_UNAUTHENTICATED"))
                .andExpect(header().exists("X-Request-Id"));
    }
}
