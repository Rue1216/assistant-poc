package com.example.copilot.aiassistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
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

    private static Set<String> properties(AssistantTool<?> tool) {
        JsonObjectSchema schema = tool.specification().parameters();
        return schema == null ? Set.of() : schema.properties().keySet();
    }
}
