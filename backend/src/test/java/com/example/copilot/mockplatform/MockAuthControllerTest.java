package com.example.copilot.mockplatform;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class MockAuthControllerTest extends IntegrationTestBase {

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/mock/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void loginWithValidCredentialsCreatesSession() throws Exception {
        MockHttpSession session = login("fc.officer", "poc1234");
        mockMvc.perform(get("/api/mock/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fc.officer"))
                .andExpect(jsonPath("$.roleKey").value("FC_OFFICER"))
                .andExpect(jsonPath("$.permissions", hasItem("AI_ASSISTANT_USE")))
                .andExpect(jsonPath("$.permissions", not(hasItem("AI_CONFIG_READ"))));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/mock/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fc.officer\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AI_UNAUTHENTICATED"));
    }

    @Test
    void itAdminHasConfigPermissions() throws Exception {
        MockHttpSession session = login("it.admin", "poc1234");
        mockMvc.perform(get("/api/mock/auth/me").session(session))
                .andExpect(jsonPath("$.permissions", hasItem("AI_CONFIG_READ")))
                .andExpect(jsonPath("$.permissions", hasItem("AI_CONFIG_EDIT")))
                .andExpect(jsonPath("$.permissions", hasItem("AI_CONFIG_PUBLISH")));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login("fc.officer", "poc1234");
        mockMvc.perform(post("/api/mock/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mock/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedAssistantApiReturnsAiUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/ai-assistant/customers/C001/suggestions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AI_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/mock/auth/logout").with(TestUsers.as("fc.officer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void directoryHasNineAccountsWithExpectedScopes() {
        MockUserDirectory dir = new MockUserDirectory();
        org.assertj.core.api.Assertions.assertThat(dir.all()).hasSize(9);
        MockUser north = dir.find("region.north").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(north.regionBranchCodes()).containsExactlyInAnyOrder("B101", "B102");
        org.assertj.core.api.Assertions.assertThat(dir.find("ao.officer").orElseThrow().marketingUnit()).isEqualTo("MU-N1");
        org.assertj.core.api.Assertions.assertThat(dir.find("it.admin").orElseThrow().branchCode()).isEqualTo("HQ");
    }
}
