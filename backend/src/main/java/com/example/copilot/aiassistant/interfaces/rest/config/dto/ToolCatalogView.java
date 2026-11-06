package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.ToolOption;
import java.util.List;

/** The registered tools the prompt editor offers in its tool picker; nothing beyond name, purpose and options. */
public record ToolCatalogView(List<ToolView> tools) {

    public record ToolView(String name, String purpose, List<OptionView> options) {

        static ToolView of(AssistantTool<?> tool) {
            return new ToolView(tool.name(), tool.purpose(), tool.options().stream().map(OptionView::of).toList());
        }
    }

    public record OptionView(String key, String name, String description) {

        static OptionView of(ToolOption option) {
            return new OptionView(option.key(), option.name(), option.description());
        }
    }

    public static ToolCatalogView of(List<AssistantTool<?>> tools) {
        return new ToolCatalogView(tools.stream().map(ToolView::of).toList());
    }
}
