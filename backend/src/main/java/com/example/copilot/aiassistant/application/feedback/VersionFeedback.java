package com.example.copilot.aiassistant.application.feedback;

/** Thumbs-up and thumbs-down counts of one published prompt version. */
public record VersionFeedback(int version, long up, long down) {
}
