package com.example.copilot.aiassistant.domain.exception;

/** Names the input field that was rejected. */
public record FieldDetail(String field) implements ErrorDetail {
}
