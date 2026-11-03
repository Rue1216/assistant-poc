package com.example.copilot.aiassistant.domain.config;

import com.example.copilot.aiassistant.domain.exception.ErrorDetail;

/** One pre-publish validation failure; {@code message} is user-facing (Chinese). */
public record ValidationError(String rule, String message) implements ErrorDetail {
}
