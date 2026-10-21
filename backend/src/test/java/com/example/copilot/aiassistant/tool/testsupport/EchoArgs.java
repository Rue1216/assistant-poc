package com.example.copilot.aiassistant.tool.testsupport;

import jakarta.validation.constraints.Pattern;

public record EchoArgs(@Pattern(regexp = "^\\d{4,6}$") String stockCode) {
}
