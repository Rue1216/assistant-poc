package com.example.copilot.aiassistant.config.dto;

import com.example.copilot.aiassistant.config.ConfigType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateConfigRequest(
        @NotNull @Pattern(regexp = ConfigKeys.PATTERN) String configKey,
        @NotNull ConfigType configType,
        @NotBlank @Size(max = 50) @Pattern(regexp = ConfigKeys.NAME_PATTERN) String name) {
}
