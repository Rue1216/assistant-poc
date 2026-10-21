package com.example.copilot.aiassistant.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ChatRequestBody(@NotNull String message,
                              List<@NotNull @Valid HistoryItem> history) {
}
