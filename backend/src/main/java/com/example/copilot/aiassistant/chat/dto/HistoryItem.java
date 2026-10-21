package com.example.copilot.aiassistant.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One earlier turn sent back by the front end; only {@code user} and {@code assistant} roles are accepted. */
public record HistoryItem(@NotNull @Pattern(regexp = "user|assistant") String role,
                          @NotBlank @Size(max = 4000) String content) {
}
