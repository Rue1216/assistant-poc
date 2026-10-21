package com.example.copilot.aiassistant.config.dto;

import com.example.copilot.aiassistant.config.SaveMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code content} may be blank while the version is a DRAFT; the byte limit is checked by the controller. */
public record SaveDraftRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull String content,
        @Size(max = 200) String changeNote,
        @NotNull SaveMode saveMode,
        @NotNull Long docVersion) {
}
