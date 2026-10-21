package com.example.copilot.aiassistant.config.dto;

public record ApplyRequest(Long workingCopyDocVersion, Boolean confirmOverwrite) {

    public boolean confirmed() {
        return Boolean.TRUE.equals(confirmOverwrite);
    }
}
