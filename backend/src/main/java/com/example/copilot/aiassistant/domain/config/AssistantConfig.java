package com.example.copilot.aiassistant.domain.config;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One version of a prompt config (04 section 2.1). Immutable: every change returns a copy. {@code id} and
 * {@code docVersion} are assigned by the store on insert; a write is conditional on the {@code docVersion} read.
 * The lifecycle rules (what may be edited, discarded, applied or scheduled) live here.
 */
public record AssistantConfig(String id, ConfigKey configKey, ConfigType configType, String name, String content,
                              ConfigStatus status, int version, Integer basedOnVersion, String changeNote,
                              String createdBy, Instant createdAt, String updatedBy, Instant updatedAt,
                              Instant scheduledAt, Long docVersion) {

    /** The actor recorded for changes the system makes on its own (seeding, scheduled-publish rejection). */
    public static final String SYSTEM_ACTOR = "system";

    private static final Set<ConfigStatus> APPLICABLE =
            EnumSet.of(ConfigStatus.PUBLISHED, ConfigStatus.ARCHIVED, ConfigStatus.DISCARDED);

    /** A new, not yet stored DRAFT created and last updated by {@code actor} at {@code now}. */
    public static AssistantConfig newDraft(ConfigKey configKey, ConfigType type, String name, String content,
                                           int version, String actor, Instant now) {
        return new AssistantConfig(null, configKey, type, name, content, ConfigStatus.DRAFT, version, null, null,
                actor, now, actor, now, null, null);
    }

    /** The first version (v1 DRAFT) of a new key; the key's kind must match the config type. */
    public static AssistantConfig newKey(ConfigKey key, ConfigType type, String name, String content, String actor,
                                         Instant now) {
        boolean keyMatchesType = type == ConfigType.SYSTEM_PROMPT ? key.isSystemPrompt() : key.isInstruction();
        if (!keyMatchesType) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail("configKey")));
        }
        return newDraft(key, type, name, content, 1, actor, now);
    }

    /** A write is conditional on the {@code docVersion} the caller read. */
    public void requireDocVersion(Long expected) {
        if (!Objects.equals(docVersion, expected)) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
    }

    /** Only a DRAFT can be discarded. */
    public void requireDiscardable() {
        if (status != ConfigStatus.DRAFT) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
    }

    /** Soft delete: only a DRAFT can be discarded; it keeps its {@code scheduledAt}. */
    public AssistantConfig discard(String actor, Instant now) {
        requireDiscardable();
        return replaced(actor, now);
    }

    /** Marks a working copy (DRAFT or SCHEDULED) DISCARDED because an applied version takes its place. */
    public AssistantConfig replaced(String actor, Instant now) {
        return withStatus(ConfigStatus.DISCARDED, scheduledAt).touched(actor, now);
    }

    /** Only a PUBLISHED, ARCHIVED or DISCARDED version can be copied into a new DRAFT. */
    public void requireApplicable() {
        if (!APPLICABLE.contains(status)) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
    }

    /** True when saving in {@code mode} leaves a SCHEDULED version scheduled. */
    public boolean keepsScheduleOnSave(SaveMode mode) {
        return status == ConfigStatus.SCHEDULED && mode == SaveMode.KEEP_STATUS;
    }

    /** Applies an edit; unless the schedule is kept, a SCHEDULED version goes back to DRAFT. */
    public AssistantConfig edit(String name, String content, String changeNote, SaveMode mode, String actor,
                                Instant now) {
        AssistantConfig base = status == ConfigStatus.SCHEDULED && !keepsScheduleOnSave(mode)
                ? withStatus(ConfigStatus.DRAFT, null)
                : this;
        return base.withContent(name, content, changeNote).touched(actor, now);
    }

    public AssistantConfig schedule(Instant at, String actor, Instant now) {
        return touched(actor, now).withStatus(ConfigStatus.SCHEDULED, at);
    }

    public AssistantConfig publishNow(String actor, Instant now) {
        return touched(actor, now).withStatus(ConfigStatus.PUBLISHED, null);
    }

    /** A new, not yet stored DRAFT with this version's type, name and content, based on this version. */
    public AssistantConfig copyAsDraft(int nextVersion, String actor, Instant now) {
        return newDraft(configKey, configType, name, content, nextVersion, actor, now).withBasedOnVersion(version);
    }

    // Still public: SeedLoader and persistence tests build fixtures with them.
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
