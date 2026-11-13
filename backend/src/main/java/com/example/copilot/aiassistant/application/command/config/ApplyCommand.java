package com.example.copilot.aiassistant.application.command.config;

/** {@code workingCopyDocVersion} is the docVersion of the DRAFT/SCHEDULED copy the user saw, or null if none. */
public record ApplyCommand(int sourceVersion, Long workingCopyDocVersion, boolean confirmOverwrite) {
}
