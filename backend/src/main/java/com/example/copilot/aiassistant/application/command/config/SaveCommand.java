package com.example.copilot.aiassistant.application.command.config;

import com.example.copilot.aiassistant.domain.config.SaveMode;

public record SaveCommand(String name, String content, String changeNote, SaveMode saveMode, long docVersion) {
}
