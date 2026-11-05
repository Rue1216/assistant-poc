package com.example.copilot.aiassistant.domain.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.tool.customer.CustomerSection;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Scans every AssistantTool bean, including tools added by later tasks. */
class ToolSpecificationConsistencyTest extends IntegrationTestBase {

    @Autowired
    List<AssistantTool<?>> tools;

    @Test
    void specPropertiesMatchArgsRecord() {
        assertThat(tools).isNotEmpty();
        for (AssistantTool<?> tool : tools) {
            Set<String> components = Arrays.stream(tool.argsType().getRecordComponents())
                    .map(RecordComponent::getName).collect(Collectors.toSet());
            assertThat(properties(tool)).as(tool.name()).isEqualTo(components);
        }
    }

    @Test
    void specDisallowsAdditionalProperties() {
        for (AssistantTool<?> tool : tools) {
            assertThat(tool.specification().parameters().additionalProperties()).as(tool.name()).isFalse();
            assertThat(tool.specification().name()).isEqualTo(tool.name());
        }
    }

    @Test
    void customerToolsHaveNoCustomerParameter() {
        for (AssistantTool<?> tool : tools) {
            assertThat(properties(tool)).as(tool.name())
                    .noneMatch(name -> name.toLowerCase().contains("customer"));
        }
    }

    @Test
    void inputMetadataIsConsistent() {
        for (AssistantTool<?> tool : tools) {
            if (!tool.requiredInputs().isEmpty()) {
                assertThat(tool.inputRequest()).as(tool.name()).isNotNull();
            }
        }
        AssistantTool<?> customer = tool("getCustomer");
        assertThat(customer.requiredInputs()).isEmpty();
        assertThat(customer.options()).extracting(ToolOption::key)
                .containsAll(Arrays.stream(CustomerSection.values()).map(CustomerSection::key).toList());
        assertThat(tool("getStockQuote").requiredInputs()).containsExactly("stockCode");
        assertThat(tool("getCompanyAnnouncements").requiredInputs()).containsExactly("stockCode");
    }

    private AssistantTool<?> tool(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    private static Set<String> properties(AssistantTool<?> tool) {
        JsonObjectSchema schema = tool.specification().parameters();
        return schema == null ? Set.of() : schema.properties().keySet();
    }
}
