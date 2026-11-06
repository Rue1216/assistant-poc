package com.example.copilot.aiassistant.interfaces.rest.config;

import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.ToolCatalogView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tool catalog for the prompt editor's tool picker. */
@RestController
@RequestMapping("/api/ai-assistant/admin/tools")
public class AdminToolController {

    private final ToolRegistry registry;

    public AdminToolController(ToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AI_CONFIG_READ')")
    public ToolCatalogView catalog() {
        return ToolCatalogView.of(registry.all());
    }
}
