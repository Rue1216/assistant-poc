package com.example.copilot.aiassistant.application.config;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.application.support.ClasspathText;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.ValidationError;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Prompt config lifecycle (04 section 2.4, 02 sections 3.1-3.10). Every write is conditional on
 * {@code docVersion}; publishing and applying are atomic in the repository. No operation deletes a document.
 */
@Service
public class ConfigCommandService {

    private static final Logger log = LoggerFactory.getLogger(ConfigCommandService.class);

    private static final Set<ConfigStatus> APPLICABLE =
            EnumSet.of(ConfigStatus.PUBLISHED, ConfigStatus.ARCHIVED, ConfigStatus.DISCARDED);
    private static final String INSTRUCTION_TEMPLATE = "seed/instruction-template.md";

    private final AssistantConfigRepository repository;
    private final ConfigValidator validator;
    private final PublishedConfigCache cache;
    private final Clock clock;
    private final AiAssistantProperties.Config limits;
    private final String instructionTemplate;

    public ConfigCommandService(AssistantConfigRepository repository, ConfigValidator validator, PublishedConfigCache cache,
                         Clock clock, AiAssistantProperties properties) {
        this.repository = repository;
        this.validator = validator;
        this.cache = cache;
        this.clock = clock;
        this.limits = properties.config();
        this.instructionTemplate = ClasspathText.read(INSTRUCTION_TEMPLATE);
    }

    /** Creates a new key with a v1 DRAFT; instructions are prefilled with the 07 section 4.1 template. */
    public AssistantConfig createKey(String configKey, ConfigType type, String name, String actor) {
        String prefix = type == ConfigType.SYSTEM_PROMPT ? "system." : "instruction.";
        if (!configKey.startsWith(prefix)) {
            throw invalidInput("configType");
        }
        if (repository.findLatest(configKey).isPresent()
                || (type == ConfigType.SYSTEM_PROMPT && repository.existsByConfigType(ConfigType.SYSTEM_PROMPT))) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        String content = type == ConfigType.SYSTEM_INSTRUCTION ? instructionTemplate : "";
        return repository.insert(AssistantConfig.newDraft(configKey, type, name, content, 1, actor, clock.instant()));
    }

    /** Copies the published version (or, if none, the latest one) into a new DRAFT. */
    public AssistantConfig createDraft(String configKey, String actor) {
        if (workingCopy(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        AssistantConfig base = repository.findPublished(configKey)
                .or(() -> repository.findLatest(configKey))
                .orElseThrow(ConfigCommandService::notFound);
        return repository.insert(AssistantConfig.newDraft(configKey, base.configType(), base.name(), base.content(),
                nextVersion(configKey), actor, clock.instant()));
    }

    public AssistantConfig saveWorkingCopy(String configKey, SaveCommand cmd, String actor) {
        AssistantConfig wc = requireWorkingCopy(configKey, cmd.docVersion());
        if (wc.status() == ConfigStatus.SCHEDULED) {
            if (cmd.saveMode() == SaveMode.KEEP_STATUS) {
                // The schedule stays, so the new content must still be publishable when it falls due.
                requireValid(wc.configType(), cmd.name(), cmd.content());
            } else {
                wc = wc.withStatus(ConfigStatus.DRAFT, null);
            }
        }
        return repository.save(wc.withContent(cmd.name(), cmd.content(), cmd.changeNote())
                .touched(actor, clock.instant()));
    }

    /** Soft delete: the DRAFT becomes DISCARDED and stays in the history. */
    public void discardDraft(String configKey, long docVersion, String actor) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        if (wc.status() != ConfigStatus.DRAFT) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        requireDocVersion(wc, docVersion);
        repository.save(wc.withStatus(ConfigStatus.DISCARDED, wc.scheduledAt()).touched(actor, clock.instant()));
    }

    public AssistantConfig publish(String configKey, PublishCommand cmd, String actor) {
        Instant now = clock.instant();
        if (cmd.mode() == PublishMode.SCHEDULED) {
            requireScheduleWindow(cmd.scheduledAt(), now);
        }
        AssistantConfig wc = requireWorkingCopy(configKey, cmd.docVersion());
        requireValid(wc.configType(), wc.name(), wc.content());
        wc = wc.touched(actor, now);
        if (cmd.mode() == PublishMode.SCHEDULED) {
            return repository.save(wc.withStatus(ConfigStatus.SCHEDULED, cmd.scheduledAt()));
        }
        // scheduledAt is kept as it was: publishing a SCHEDULED copy right away does not clear it.
        AssistantConfig published = repository.publish(wc.withStatus(ConfigStatus.PUBLISHED, wc.scheduledAt()));
        cache.invalidate();
        return published;
    }

    /**
     * Copies a PUBLISHED, ARCHIVED or DISCARDED version into a new DRAFT. An existing working copy is
     * marked DISCARDED first, in the same transaction, so there is never more than one DRAFT per key.
     * Nothing is published, so the prompt cache is left alone.
     */
    public AssistantConfig applyVersion(String configKey, ApplyCommand cmd, String actor) {
        AssistantConfig source = repository.findVersion(configKey, cmd.sourceVersion())
                .orElseThrow(ConfigCommandService::notFound);
        if (!APPLICABLE.contains(source.status())) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        Optional<AssistantConfig> replaced = workingCopy(configKey);
        if (replaced.isPresent()) {
            if (!cmd.confirmOverwrite()) {
                throw new AssistantException(ErrorCode.AI_CONFIG_OVERWRITE_PENDING,
                        List.of(pendingDetail(replaced.get())));
            }
            requireDocVersion(replaced.get(), cmd.workingCopyDocVersion());
        } else if (cmd.workingCopyDocVersion() != null) {
            // The working copy the user saw is gone (published by the scheduler or discarded).
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        Instant now = clock.instant();
        AssistantConfig discarded = replaced
                .map(old -> old.withStatus(ConfigStatus.DISCARDED, old.scheduledAt()).touched(actor, now))
                .orElse(null);
        AssistantConfig draft = AssistantConfig.newDraft(configKey, source.configType(), source.name(),
                source.content(), nextVersion(configKey), actor, now).withBasedOnVersion(source.version());
        return repository.replaceWorkingCopy(discarded, draft);
    }

    /**
     * Publishes every SCHEDULED version whose time has come and returns how many were published. A version
     * changed since it was read (or published by another instance) is skipped by the repository.
     */
    public int publishDue() {
        Instant now = clock.instant();
        int published = 0;
        for (AssistantConfig due : repository.findScheduledDue(now)) {
            try {
                if (repository.publishScheduled(due, now)) {
                    published++;
                }
            } catch (RuntimeException e) {
                log.warn("scheduled publish failed configKey={} version={} error={}",
                        due.configKey(), due.version(), e.getClass().getName());
            }
        }
        if (published > 0) {
            cache.invalidate();
        }
        return published;
    }

    public List<ValidationError> validateWorkingCopy(String configKey) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        return validator.validate(wc.configType(), wc.name(), wc.content());
    }

    /** The DRAFT or SCHEDULED version of the key, if any. */
    public Optional<AssistantConfig> findWorkingCopy(String configKey) {
        return workingCopy(configKey);
    }

    private Optional<AssistantConfig> workingCopy(String configKey) {
        return repository.findWorkingCopy(configKey);
    }

    private AssistantConfig requireWorkingCopy(String configKey, long docVersion) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        requireDocVersion(wc, docVersion);
        return wc;
    }

    /**
     * An unknown key is NOT_FOUND; a known key without a DRAFT/SCHEDULED copy means the copy the caller saw
     * was published, discarded or applied over by someone else in the meantime, which is a CONFLICT.
     */
    private AssistantConfig existingWorkingCopy(String configKey) {
        Optional<AssistantConfig> wc = workingCopy(configKey);
        if (wc.isPresent()) {
            return wc.get();
        }
        if (repository.findLatest(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        throw notFound();
    }

    private static void requireDocVersion(AssistantConfig config, Long expected) {
        if (!Objects.equals(config.docVersion(), expected)) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
    }

    private void requireValid(ConfigType type, String name, String content) {
        List<ValidationError> errors = validator.validate(type, name, content);
        if (!errors.isEmpty()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_VALIDATION_FAILED, List.copyOf(errors));
        }
    }

    private void requireScheduleWindow(Instant scheduledAt, Instant now) {
        if (scheduledAt == null
                || scheduledAt.isBefore(now.plus(limits.scheduleMinLead()))
                || scheduledAt.isAfter(now.plus(limits.scheduleMaxLead()))) {
            throw invalidInput("scheduledAt");
        }
    }

    private int nextVersion(String configKey) {
        return repository.findLatest(configKey).map(c -> c.version() + 1).orElse(1);
    }

    private static Map<String, Object> pendingDetail(AssistantConfig wc) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", wc.status().name());
        detail.put("scheduledAt", wc.scheduledAt());
        detail.put("updatedAt", wc.updatedAt());
        detail.put("docVersion", wc.docVersion());
        return detail;
    }

    private static AssistantException notFound() {
        return new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
    }

    private static AssistantException invalidInput(String field) {
        return new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(Map.of("field", field)));
    }
}
