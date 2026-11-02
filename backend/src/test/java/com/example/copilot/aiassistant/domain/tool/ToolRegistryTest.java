package com.example.copilot.aiassistant.domain.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.tool.testsupport.FakeTool;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private static FakeTool<NoArgs> tool(String name, AccessLevel level) {
        return new FakeTool<>(name, NoArgs.class, level, Duration.ofSeconds(1),
                (a, c) -> ToolResult.success(null, null));
    }

    @Test
    void limitedGetsOnlyLimitedTools() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("market", AccessLevel.LIMITED),
                tool("interactions", AccessLevel.FULL)));

        assertThat(registry.toolsFor(AccessLevel.LIMITED)).extracting(AssistantTool::name).containsExactly("market");
        assertThat(registry.toolsFor(AccessLevel.FULL)).extracting(AssistantTool::name)
                .containsExactly("market", "interactions");
        assertThat(registry.toolsFor(AccessLevel.NONE)).isEmpty();
        assertThat(registry.find("market")).isPresent();
        assertThat(registry.find("nope")).isEmpty();
    }

    @Test
    void duplicateNamesFail() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(tool("a", AccessLevel.LIMITED), tool("a", AccessLevel.FULL))))
                .isInstanceOf(IllegalStateException.class);
    }
}
