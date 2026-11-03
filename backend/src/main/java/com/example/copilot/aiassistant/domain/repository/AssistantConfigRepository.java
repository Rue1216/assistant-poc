package com.example.copilot.aiassistant.domain.repository;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores prompt config versions. Writes that lose to a concurrent change (stale {@code docVersion}, unique index,
 * transaction write conflict) throw {@code AssistantException(AI_CONFIG_CONFLICT)}; other store failures propagate.
 */
public interface AssistantConfigRepository {

    Optional<AssistantConfig> findPublished(String configKey);

    /** The DRAFT or SCHEDULED version of the key, if any. */
    Optional<AssistantConfig> findWorkingCopy(String configKey);

    /** The highest version of the key. */
    Optional<AssistantConfig> findLatest(String configKey);

    Optional<AssistantConfig> findVersion(String configKey, int version);

    /** Every PUBLISHED version, for the prompt cache. */
    List<AssistantConfig> findPublished();

    /** SCHEDULED versions whose {@code scheduledAt} is at or before {@code now}. */
    List<AssistantConfig> findScheduledDue(Instant now);

    long count();

    /** Stores a new version; a unique index violation is a conflict. */
    AssistantConfig insert(AssistantConfig config);

    /** Updates the version conditionally on its {@code docVersion}; returns it with the incremented docVersion. */
    AssistantConfig save(AssistantConfig config);

    /** In one transaction: archives the key's current PUBLISHED version and stores the given one (PUBLISHED). */
    AssistantConfig publish(AssistantConfig workingCopy);

    /** In one transaction: stores {@code discarded} (may be null) and inserts {@code draft}. */
    AssistantConfig replaceWorkingCopy(AssistantConfig discarded, AssistantConfig draft);

    /**
     * In one transaction: archives the current PUBLISHED version and claims {@code due} if it is still SCHEDULED
     * with the same docVersion. Returns false, with nothing changed, when another change won the race.
     */
    boolean publishScheduled(AssistantConfig due, Instant now);

    /**
     * Turns {@code due} back into a DRAFT (clearing {@code scheduledAt}) if it is still SCHEDULED with the same
     * docVersion. Returns false, with nothing changed, when another change won the race. No transaction.
     */
    boolean revertScheduled(AssistantConfig due, String actor, Instant now);
}
