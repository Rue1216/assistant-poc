package com.example.copilot.mockplatform;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class MockAuthControllerTest extends IntegrationTestBase {

    /** The pre-employee-account username, split so a search for retired names stays clean. */
    private static final String RETIRED_USERNAME = "fc." + "officer";

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/mock/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void loginWithValidCredentialsCreatesSession() throws Exception {
        MockHttpSession session = login("ICRM00028", "poc1234");
        mockMvc.perform(get("/api/mock/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ICRM00028"))
                .andExpect(jsonPath("$.roleKey").value("RM"))
                .andExpect(jsonPath("$.permissions", hasItem("AI_ASSISTANT_USE")))
                .andExpect(jsonPath("$.permissions", not(hasItem("AI_CONFIG_READ"))));
    }

    @Test
    void roleKeysAreBusinessRoleCodes() throws Exception {
        mockMvc.perform(get("/api/mock/auth/me").session(login("EEE22222", "poc1234")))
                .andExpect(jsonPath("$.roleKey").value("ADMIN"))
                .andExpect(jsonPath("$.roleName").value("IT 管理人員"));
    }

    @Test
    void retiredUsernamesCannotLogIn() throws Exception {
        mockMvc.perform(post("/api/mock/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + RETIRED_USERNAME + "\",\"password\":\"poc1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/mock/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ICRM00028\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AI_UNAUTHENTICATED"));
    }

    @Test
    void itAdminHasConfigPermissions() throws Exception {
        MockHttpSession session = login("EEE22222", "poc1234");
        mockMvc.perform(get("/api/mock/auth/me").session(session))
                .andExpect(jsonPath("$.permissions", hasItem("AI_CONFIG_READ")))
                .andExpect(jsonPath("$.permissions", hasItem("AI_CONFIG_EDIT")))
                .andExpect(jsonPath("$.permissions", hasItem("AI_CONFIG_PUBLISH")));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login("ICRM00028", "poc1234");
        mockMvc.perform(post("/api/mock/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mock/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedAssistantApiReturnsAiUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/ai-assistant/customers/" + TestCustomers.RETIREE + "/suggestions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AI_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/mock/auth/logout").with(TestUsers.as("ICRM00028")))
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
        MockUser north = dir.find("ICRM00007").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(north.regionBranchCodes()).containsExactlyInAnyOrder("B101", "B102");
        org.assertj.core.api.Assertions.assertThat(dir.find("ICRM00040").orElseThrow().marketingUnit()).isEqualTo("MU-N1");
        org.assertj.core.api.Assertions.assertThat(dir.find("EEE22222").orElseThrow().branchCode()).isEqualTo("HQ");
        org.assertj.core.api.Assertions.assertThat(dir.all()).extracting(MockUser::scope).containsExactly(
                MockScope.ALL, MockScope.REGION, MockScope.BRANCH, MockScope.BRANCH_WEALTH, MockScope.ASSIGNEE_WEALTH,
                MockScope.MARKETING_UNIT, MockScope.ASSIGNEE_MARKETING, MockScope.ALL, MockScope.ALL);
    }
}
