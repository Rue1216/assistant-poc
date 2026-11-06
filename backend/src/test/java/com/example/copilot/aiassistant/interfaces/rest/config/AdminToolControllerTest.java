package com.example.copilot.aiassistant.interfaces.rest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestUsers;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class AdminToolControllerTest extends IntegrationTestBase {

    private static final String URL = "/api/ai-assistant/admin/tools";
    private static final String ADMIN = "EEE22222";

    @Autowired ToolRegistry registry;

    private ResultActions call() throws Exception {
        return mockMvc.perform(get(URL).with(TestUsers.as(ADMIN)));
    }

    private String body() throws Exception {
        return call().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void adminGetsTheCatalogInRegistryOrder() throws Exception {
        List<String> names = registry.all().stream().map(AssistantTool::name).toList();

        assertThat(JsonPath.<List<String>>read(body(), "$.tools[*].name")).isEqualTo(names);
    }

    @Test
    void getCustomerListsEverySection() throws Exception {
        List<Map<String, String>> options = JsonPath.read(body(), "$.tools[?(@.name=='getCustomer')].options[*]");

        assertThat(options).hasSize(6);
        assertThat(options).filteredOn(o -> o.get("name").equals("顧客經營"))
                .singleElement().extracting(o -> o.get("description")).isEqualTo("專屬活動、建議商品、經營洞察");
    }

    @Test
    void toolWithoutOptionsHasAnEmptyList() throws Exception {
        List<List<Object>> options = JsonPath.read(body(), "$.tools[?(@.name=='getStockQuote')].options");

        assertThat(options).hasSize(1);
        assertThat(options.get(0)).isEmpty();
    }

    @Test
    void onlyTheAgreedFieldsAreReturned() throws Exception {
        call().andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[0].name").exists())
                .andExpect(jsonPath("$.tools[0].purpose").exists())
                .andExpect(jsonPath("$.tools[0].options").exists())
                .andExpect(jsonPath("$.tools[0].requiredAccessLevel").doesNotExist())
                .andExpect(jsonPath("$.tools[0].parameters").doesNotExist())
                .andExpect(jsonPath("$.tools[0].specification").doesNotExist());
    }

    @Test
    void withoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get(URL).with(TestUsers.as("ICRM00028"))).andExpect(status().isForbidden());
    }
}
