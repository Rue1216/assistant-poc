package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.domain.config.SaveMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code content} may be blank while the version is a DRAFT; the byte limit is checked by the controller. */
public record SaveDraftRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = ConfigKeys.NAME_PATTERN) String name,
        @NotNull String content,
        @Size(max = 200) String changeNote,
        @NotNull SaveMode saveMode,
        @NotNull Long docVersion) {
}
