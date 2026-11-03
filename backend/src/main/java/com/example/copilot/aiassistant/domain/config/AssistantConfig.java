package com.example.copilot.aiassistant.domain.config;

import java.time.Instant;

/**
 * One version of a prompt config (04 section 2.1). Immutable: every change returns a copy. {@code id} and
 * {@code docVersion} are assigned by the store on insert; a write is conditional on the {@code docVersion} read.
 */
public record AssistantConfig(String id, String configKey, ConfigType configType, String name, String content,
                              ConfigStatus status, int version, Integer basedOnVersion, String changeNote,
                              String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
                              Instant scheduledAt, Long docVersion) {

    /** The actor recorded for changes the system makes on its own (seeding, scheduled-publish rejection). */
    public static final String SYSTEM_ACTOR = "system";
    public static final String SYSTEM_PROMPT_KEY = "system.main";
    public static final String INSTRUCTION_KEY_PREFIX = "instruction.";

    /** A new, not yet stored DRAFT created and last updated by {@code actor} at {@code now}. */
    public static AssistantConfig newDraft(String configKey, ConfigType type, String name, String content,
                                           int version, String actor, Instant now) {
        return new AssistantConfig(null, configKey, type, name, content, ConfigStatus.DRAFT, version, null, null,
                actor, now, actor, now, null, null);
    }

    public AssistantConfig withContent(String name, String content, String changeNote) {
        return new AssistantConfig(id, configKey, configType, name, content, status, version, basedOnVersion,
                changeNote, createdBy, createdAt, updatedBy, updatedAt, scheduledAt, docVersion);
    }

    public AssistantConfig withStatus(ConfigStatus status, Instant scheduledAt) {
        return new AssistantConfig(id, configKey, configType, name, content, status, version, basedOnVersion,
                changeNote, createdBy, createdAt, updatedBy, updatedAt, scheduledAt, docVersion);
    }

    public AssistantConfig withBasedOnVersion(Integer basedOnVersion) {
        return new AssistantConfig(id, configKey, configType, name, content, status, version, basedOnVersion,
                changeNote, createdBy, createdAt, updatedBy, updatedAt, scheduledAt, docVersion);
    }

    /** Records the last editor. */
    public AssistantConfig touched(String actor, Instant now) {
        return new AssistantConfig(id, configKey, configType, name, content, status, version, basedOnVersion,
                changeNote, createdBy, createdAt, actor, now, scheduledAt, docVersion);
    }

    /** DRAFT or SCHEDULED: the one version of a key that can still be edited. */
    public boolean isWorkingCopy() {
        return status == ConfigStatus.DRAFT || status == ConfigStatus.SCHEDULED;
    }
}
