package com.example.copilot.mockplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class PlatformFilterConfigurationTest extends IntegrationTestBase {

    @Test
    void everyResponseCarriesAGeneratedTraceId() throws Exception {
        String id = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Trace-Id");
        assertThat(id).matches("[0-9a-f]{32}");
    }

    @Test
    void inboundB3TraceIdIsEchoed() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124"))
                .andExpect(header().string("X-Trace-Id", "463ac35c9f6413ad48485a3953bb6124"));
    }

    @Test
    void unauthenticatedResponseCarriesTraceId() throws Exception {
        mockMvc.perform(post("/test-support/conflict").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"));
    }
}
