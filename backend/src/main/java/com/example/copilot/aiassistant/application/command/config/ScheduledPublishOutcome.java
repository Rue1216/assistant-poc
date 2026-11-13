package com.example.copilot.aiassistant.application.command.config;

/** What {@link ConfigCommandUseCase#publishScheduled} did with one due SCHEDULED version. */
public enum ScheduledPublishOutcome {
    /** It went live; the previous PUBLISHED version was archived. */
    PUBLISHED,
    /** It no longer passes validation and was turned back into a DRAFT. */
    REVERTED,
    /** It no longer passes validation but had already been changed by someone else, so it was left alone. */
    SKIPPED
}
