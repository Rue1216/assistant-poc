package com.example.copilot.aiassistant.domain.repository;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores prompt config versions. Writes that lose to a concurrent change (stale {@code docVersion}, unique index,
 * transaction write conflict) throw {@code AssistantException(AI_CONFIG_CONFLICT)}; other store failures propagate.
 */
public interface AssistantConfigRepository {

    Optional<AssistantConfig> findPublished(ConfigKey configKey);

    /** The DRAFT or SCHEDULED version of the key, if any. */
    Optional<AssistantConfig> findWorkingCopy(ConfigKey configKey);

    /** The highest version of the key. */
    Optional<AssistantConfig> findLatest(ConfigKey configKey);

    Optional<AssistantConfig> findVersion(ConfigKey configKey, int version);

    /** Every PUBLISHED version, for the prompt cache. */
    List<AssistantConfig> findPublished();

    /** SCHEDULED versions whose {@code scheduledAt} is at or before {@code now}. */
    List<AssistantConfig> findScheduledDue(Instant now);

    long count();

    /** Stores a new version; a unique index violation is a conflict. */
    AssistantConfig insert(AssistantConfig config);

    /** Updates the version conditionally on its {@code docVersion}; returns it with the incremented docVersion. */
    AssistantConfig save(AssistantConfig config);

    /**
     * Archives the key's current PUBLISHED version and stores the given one (PUBLISHED). Several writes: the caller
     * runs it in a transaction.
     */
    AssistantConfig publish(AssistantConfig workingCopy);

    /** Stores {@code discarded} (may be null) and inserts {@code draft}. Several writes: the caller runs it in a transaction. */
    AssistantConfig replaceWorkingCopy(AssistantConfig discarded, AssistantConfig draft);

    /**
     * Archives the current PUBLISHED version and claims {@code due} if it is still SCHEDULED with the same
     * docVersion; the caller runs it in a transaction. When another change won the race it throws
     * {@code AI_CONFIG_CONFLICT}, so the caller's transaction rolls the archive back. Store failures (including
     * write conflicts) propagate unmapped.
     */
    void publishScheduled(AssistantConfig due, Instant now);

    /**
     * Turns {@code due} back into a DRAFT (clearing {@code scheduledAt}) if it is still SCHEDULED with the same
     * docVersion. Returns false, with nothing changed, when another change won the race. A single conditional write.
     */
    boolean revertScheduled(AssistantConfig due, String actor, Instant now);
}
