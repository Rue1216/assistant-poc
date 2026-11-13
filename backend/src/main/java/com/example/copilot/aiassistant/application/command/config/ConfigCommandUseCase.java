package com.example.copilot.aiassistant.application.command.config;

import com.example.copilot.aiassistant.application.support.ClasspathText;
import com.example.copilot.aiassistant.application.support.Transactions;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.ScheduleWindow;
import com.example.copilot.aiassistant.domain.config.ValidationError;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import com.example.copilot.aiassistant.domain.exception.WorkingCopyDetail;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.example.shared.annotation.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prompt config lifecycle (04 section 2.4, 02 sections 3.1-3.10). Every write is conditional on
 * {@code docVersion}; publishing and applying run in one Mongo transaction each. No operation deletes a document.
 * Going live publishes a {@link ConfigPublishedEvent}; the prompt cache drops its entry once the transaction commits.
 */
@UseCase
@Slf4j
public class ConfigCommandUseCase {

    private static final String INSTRUCTION_TEMPLATE = "seed/instruction-template.md";

    private final AssistantConfigRepository repository;
    private final ConfigValidator validator;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ScheduleWindow scheduleWindow;
    private final String instructionTemplate;

    public ConfigCommandUseCase(AssistantConfigRepository repository, ConfigValidator validator,
                                ApplicationEventPublisher events, Clock clock, ScheduleWindow scheduleWindow) {
        this.repository = repository;
        this.validator = validator;
        this.events = events;
        this.clock = clock;
        this.scheduleWindow = scheduleWindow;
        this.instructionTemplate = ClasspathText.read(INSTRUCTION_TEMPLATE);
    }

    /** Creates a new key with a v1 DRAFT; instructions are prefilled with the 07 section 4.1 template. */
    public AssistantConfig createKey(ConfigKey configKey, ConfigType type, String name, String actor) {
        String content = type == ConfigType.SYSTEM_INSTRUCTION ? instructionTemplate : "";
        AssistantConfig first = AssistantConfig.newKey(configKey, type, name, content, actor, clock.instant());
        if (repository.findLatest(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        try {
            return repository.insert(first);
        } catch (AssistantException e) {
            if (e.code() == ErrorCode.AI_CONFIG_CONFLICT) {
                // A brand-new key has no docVersion to disagree on, so a conflict means someone created it first.
                throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
            }
            throw e;
        }
    }

    /** Copies the published version (or, if none, the latest one) into a new DRAFT. */
    public AssistantConfig createDraft(ConfigKey configKey, String actor) {
        if (workingCopy(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        AssistantConfig base = repository.findPublished(configKey)
                .or(() -> repository.findLatest(configKey))
                .orElseThrow(ConfigCommandUseCase::notFound);
        return repository.insert(base.copyAsDraft(nextVersion(configKey), actor, clock.instant()));
    }

    public AssistantConfig saveWorkingCopy(ConfigKey configKey, SaveCommand cmd, String actor) {
        if (cmd.content().getBytes(StandardCharsets.UTF_8).length > ConfigValidator.MAX_BYTES) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail("content")));
        }
        AssistantConfig wc = requireWorkingCopy(configKey, cmd.docVersion());
        if (wc.keepsScheduleOnSave(cmd.saveMode())) {
            // The schedule stays, so the new content must still be publishable when it falls due.
            requireValid(wc.configType(), cmd.name(), cmd.changeNote(), cmd.content());
        } else {
            requireMetadataValid(cmd.name(), cmd.changeNote());
        }
        return repository.save(wc.edit(cmd.name(), cmd.content(), cmd.changeNote(), cmd.saveMode(), actor,
                clock.instant()));
    }

    /** Soft delete: the DRAFT becomes DISCARDED and stays in the history. */
    public void discardDraft(ConfigKey configKey, long docVersion, String actor) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        wc.requireDiscardable();
        wc.requireDocVersion(docVersion);
        repository.save(wc.discard(actor, clock.instant()));
    }

    /**
     * Schedules the working copy, or publishes it now: archives the current PUBLISHED version and publishes the
     * working copy in one transaction, then announces it (only going live publishes a {@link ConfigPublishedEvent}).
     */
    @Transactional(transactionManager = Transactions.MONGO)
    public AssistantConfig publish(ConfigKey configKey, PublishCommand cmd, String actor) {
        Instant now = clock.instant();
        if (cmd.mode() == PublishMode.SCHEDULED) {
            scheduleWindow.requireWithin(cmd.scheduledAt(), now);
        }
        AssistantConfig wc = requireWorkingCopy(configKey, cmd.docVersion());
        requireValid(wc.configType(), wc.name(), wc.changeNote(), wc.content());
        if (cmd.mode() == PublishMode.SCHEDULED) {
            return repository.save(wc.schedule(cmd.scheduledAt(), actor, now));
        }
        AssistantConfig published = repository.publish(wc.publishNow(actor, now));
        events.publishEvent(new ConfigPublishedEvent(configKey));
        return published;
    }

    /**
     * Copies a PUBLISHED, ARCHIVED or DISCARDED version into a new DRAFT. An existing working copy is
     * marked DISCARDED first, in the same transaction, so there is never more than one DRAFT per key.
     * Nothing is published, so the prompt cache is left alone.
     */
    @Transactional(transactionManager = Transactions.MONGO)
    public AssistantConfig applyVersion(ConfigKey configKey, ApplyCommand cmd, String actor) {
        AssistantConfig source = repository.findVersion(configKey, cmd.sourceVersion())
                .orElseThrow(ConfigCommandUseCase::notFound);
        source.requireApplicable();
        Optional<AssistantConfig> replaced = workingCopy(configKey);
        if (replaced.isPresent()) {
            if (!cmd.confirmOverwrite()) {
                throw new AssistantException(ErrorCode.AI_CONFIG_OVERWRITE_PENDING,
                        List.of(pendingDetail(replaced.get())));
            }
            replaced.get().requireDocVersion(cmd.workingCopyDocVersion());
        } else if (cmd.workingCopyDocVersion() != null) {
            // The working copy the user saw is gone (published by the scheduler or discarded).
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        Instant now = clock.instant();
        AssistantConfig discarded = replaced
                .map(old -> old.replaced(actor, now))
                .orElse(null);
        AssistantConfig draft = source.copyAsDraft(nextVersion(configKey), actor, now);
        return repository.replaceWorkingCopy(discarded, draft);
    }

    /** SCHEDULED versions whose time has come, for the scheduler to publish one by one. */
    public List<AssistantConfig> findScheduledDue() {
        return repository.findScheduledDue(clock.instant());
    }

    /**
     * Publishes one due SCHEDULED version in its own transaction. A version that no longer passes validation is
     * reverted to DRAFT instead ({@code REVERTED}), or left alone if it changed meanwhile ({@code SKIPPED}).
     * A version changed since it was read, or published by another instance, is {@code AI_CONFIG_CONFLICT}
     * and nothing is written.
     */
    @Transactional(transactionManager = Transactions.MONGO)
    public ScheduledPublishOutcome publishScheduled(AssistantConfig due) {
        Instant now = clock.instant();
        List<ValidationError> errors = validator.validate(due.configType(), due.name(), due.changeNote(),
                due.content());
        if (!errors.isEmpty()) {
            if (repository.revertScheduled(due, AssistantConfig.SYSTEM_ACTOR, now)) {
                log.warn("scheduled publish rejected configKey={} version={} rules={}", due.configKey(),
                        due.version(), errors.stream().map(ValidationError::rule).distinct().toList());
                return ScheduledPublishOutcome.REVERTED;
            }
            return ScheduledPublishOutcome.SKIPPED;
        }
        repository.publishScheduled(due, now);
        events.publishEvent(new ConfigPublishedEvent(due.configKey()));
        return ScheduledPublishOutcome.PUBLISHED;
    }

    private Optional<AssistantConfig> workingCopy(ConfigKey configKey) {
        return repository.findWorkingCopy(configKey);
    }

    private AssistantConfig requireWorkingCopy(ConfigKey configKey, long docVersion) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        wc.requireDocVersion(docVersion);
        return wc;
    }

    /**
     * An unknown key is NOT_FOUND; a known key without a DRAFT/SCHEDULED copy means the copy the caller saw
     * was published, discarded or applied over by someone else in the meantime, which is a CONFLICT.
     */
    private AssistantConfig existingWorkingCopy(ConfigKey configKey) {
        Optional<AssistantConfig> wc = workingCopy(configKey);
        if (wc.isPresent()) {
            return wc.get();
        }
        if (repository.findLatest(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        throw notFound();
    }

    private void requireValid(ConfigType type, String name, String changeNote, String content) {
        failIfAny(validator.validate(type, name, changeNote, content));
    }

    private void requireMetadataValid(String name, String changeNote) {
        failIfAny(validator.validateMetadata(name, changeNote));
    }

    private static void failIfAny(List<ValidationError> errors) {
        if (!errors.isEmpty()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_VALIDATION_FAILED, List.copyOf(errors));
        }
    }

    private int nextVersion(ConfigKey configKey) {
        return repository.findLatest(configKey).map(c -> c.version() + 1).orElse(1);
    }

    private static WorkingCopyDetail pendingDetail(AssistantConfig wc) {
        return new WorkingCopyDetail(wc.status(), wc.scheduledAt(), wc.updatedAt(), wc.docVersion());
    }

    private static AssistantException notFound() {
        return new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
    }
}
