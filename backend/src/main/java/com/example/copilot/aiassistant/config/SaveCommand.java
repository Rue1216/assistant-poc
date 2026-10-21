package com.example.copilot.aiassistant.config;

public record SaveCommand(String name, String content, String changeNote, SaveMode saveMode, long docVersion) {
}
