package com.example.copilot.aiassistant.tool;

import com.example.copilot.aiassistant.port.AccessLevel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final Map<String, AssistantTool<?>> byName = new LinkedHashMap<>();

    public ToolRegistry(List<AssistantTool<?>> tools) {
        for (AssistantTool<?> tool : tools) {
            if (byName.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Duplicate tool name: " + tool.name());
            }
        }
    }

    /** Tools whose required level is covered by {@code level}. */
    public List<AssistantTool<?>> toolsFor(AccessLevel level) {
        return byName.values().stream().filter(t -> level.covers(t.requiredAccessLevel())).toList();
    }

    public Optional<AssistantTool<?>> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }
}
