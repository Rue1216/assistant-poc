package com.example.copilot.aiassistant.interfaces.rest.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ChatRequestBody(@NotNull String message,
                              List<@NotNull @Valid HistoryItem> history,
                              @Pattern(regexp = "^instruction\\.[a-z0-9-]{1,50}$") String categoryKey,
                              String historySummary) {
}
