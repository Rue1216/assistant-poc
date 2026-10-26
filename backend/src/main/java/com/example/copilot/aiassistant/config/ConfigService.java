package com.example.copilot.aiassistant.config;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.prompt.PublishedConfigCache;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.TransientClientSessionException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prompt config lifecycle (04 section 2.4, 02 sections 3.1-3.10). Every write is conditional on
 * {@code docVersion}; publishing and applying run in a Mongo transaction. No operation deletes a document.
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private static final List<ConfigStatus> WORKING_COPY = List.of(ConfigStatus.DRAFT, ConfigStatus.SCHEDULED);
    private static final Set<ConfigStatus> APPLICABLE =
            EnumSet.of(ConfigStatus.PUBLISHED, ConfigStatus.ARCHIVED, ConfigStatus.DISCARDED);
    private static final String INSTRUCTION_TEMPLATE = "seed/instruction-template.md";
    private static final int WRITE_CONFLICT = 112;
    private static final int DUPLICATE_KEY = 11000;

    private final AssistantConfigRepository repository;
    private final MongoTemplate mongo;
    private final ConfigValidator validator;
    private final PublishedConfigCache cache;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AiAssistantProperties.Config limits;
    private final String instructionTemplate;

    public ConfigService(AssistantConfigRepository repository, MongoTemplate mongo, ConfigValidator validator,
                         PublishedConfigCache cache, MongoTransactionManager transactionManager, Clock clock,
                         AiAssistantProperties properties) {
        this.repository = repository;
        this.mongo = mongo;
        this.validator = validator;
        this.cache = cache;
        this.tx = new TransactionTemplate(transactionManager);
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
        if (repository.findTopByConfigKeyOrderByVersionDesc(configKey).isPresent()
                || (type == ConfigType.SYSTEM_PROMPT && repository.existsByConfigType(ConfigType.SYSTEM_PROMPT))) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        String content = type == ConfigType.SYSTEM_INSTRUCTION ? instructionTemplate : "";
        AssistantConfig draft = newVersion(configKey, type, name, content, 1, actor, clock.instant());
        return write(() -> repository.insert(draft));
    }

    /** Copies the published version (or, if none, the latest one) into a new DRAFT. */
    public AssistantConfig createDraft(String configKey, String actor) {
        if (workingCopy(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        AssistantConfig base = repository.findByConfigKeyAndStatus(configKey, ConfigStatus.PUBLISHED)
                .or(() -> repository.findTopByConfigKeyOrderByVersionDesc(configKey))
                .orElseThrow(ConfigService::notFound);
        AssistantConfig draft = newVersion(configKey, base.getConfigType(), base.getName(), base.getContent(),
                nextVersion(configKey), actor, clock.instant());
        return write(() -> repository.insert(draft));
    }

    public AssistantConfig saveWorkingCopy(String configKey, SaveCommand cmd, String actor) {
        AssistantConfig wc = requireWorkingCopy(configKey, cmd.docVersion());
        if (wc.getStatus() == ConfigStatus.SCHEDULED) {
            if (cmd.saveMode() == SaveMode.KEEP_STATUS) {
                // The schedule stays, so the new content must still be publishable when it falls due.
                requireValid(wc.getConfigType(), cmd.name(), cmd.content());
            } else {
                wc.setStatus(ConfigStatus.DRAFT);
                wc.setScheduledAt(null);
            }
        }
        wc.setName(cmd.name());
        wc.setContent(cmd.content());
        wc.setChangeNote(cmd.changeNote());
        touch(wc, actor, clock.instant());
        return write(() -> repository.save(wc));
    }

    /** Soft delete: the DRAFT becomes DISCARDED and stays in the history. */
    public void discardDraft(String configKey, long docVersion, String actor) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        if (wc.getStatus() != ConfigStatus.DRAFT) {
            throw new AssistantException(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
        requireDocVersion(wc, docVersion);
        wc.setStatus(ConfigStatus.DISCARDED);
        touch(wc, actor, clock.instant());
        write(() -> repository.save(wc));
    }

    public AssistantConfig publish(String configKey, PublishCommand cmd, String actor) {
        Instant now = clock.instant();
        if (cmd.mode() == PublishMode.SCHEDULED) {
            requireScheduleWindow(cmd.scheduledAt(), now);
        }
        AssistantConfig wc = requireWorkingCopy(configKey, cmd.docVersion());
        requireValid(wc.getConfigType(), wc.getName(), wc.getContent());
        touch(wc, actor, now);
        if (cmd.mode() == PublishMode.SCHEDULED) {
            wc.setStatus(ConfigStatus.SCHEDULED);
            wc.setScheduledAt(cmd.scheduledAt());
            return write(() -> repository.save(wc));
        }
        AssistantConfig published = write(() -> tx.execute(status -> {
            archivePublished(configKey);
            wc.setStatus(ConfigStatus.PUBLISHED);
            return repository.save(wc);
        }));
        cache.invalidate();
        return published;
    }

    /**
     * Copies a PUBLISHED, ARCHIVED or DISCARDED version into a new DRAFT. An existing working copy is
     * marked DISCARDED first, in the same transaction, so there is never more than one DRAFT per key.
     * Nothing is published, so the prompt cache is left alone.
     */
    public AssistantConfig applyVersion(String configKey, ApplyCommand cmd, String actor) {
        AssistantConfig source = repository.findByConfigKeyAndVersion(configKey, cmd.sourceVersion())
                .orElseThrow(ConfigService::notFound);
        if (!APPLICABLE.contains(source.getStatus())) {
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
        return write(() -> tx.execute(status -> {
            replaced.ifPresent(old -> {
                old.setStatus(ConfigStatus.DISCARDED);
                touch(old, actor, now);
                repository.save(old);
            });
            AssistantConfig draft = newVersion(configKey, source.getConfigType(), source.getName(),
                    source.getContent(), nextVersion(configKey), actor, now);
            draft.setBasedOnVersion(source.getVersion());
            return repository.insert(draft);
        }));
    }

    /**
     * Publishes every SCHEDULED version whose time has come and returns how many were published. Each one
     * is claimed with a conditional update (still SCHEDULED, same docVersion) so that concurrent runs
     * publish it once; {@code updatedBy} stays the person who scheduled it.
     */
    public int publishDue() {
        Instant now = clock.instant();
        int published = 0;
        for (AssistantConfig due : repository.findByStatusAndScheduledAtLessThanEqual(ConfigStatus.SCHEDULED, now)) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> publishScheduled(due, now, status)))) {
                    published++;
                }
            } catch (RuntimeException e) {
                if (isLostRace(e)) {
                    // Expected when several instances run the job: the other one won.
                    log.debug("scheduled publish lost race configKey={} version={}",
                            due.getConfigKey(), due.getVersion());
                    continue;
                }
                log.warn("scheduled publish failed configKey={} version={} error={}",
                        due.getConfigKey(), due.getVersion(), e.getClass().getName());
            }
        }
        if (published > 0) {
            cache.invalidate();
        }
        return published;
    }

    public List<ConfigSummary> list() {
        Query withoutContent = new Query();
        withoutContent.fields().exclude("content");
        return mongo.find(withoutContent, AssistantConfig.class).stream()
                .collect(Collectors.groupingBy(AssistantConfig::getConfigKey))
                .values().stream()
                .map(ConfigService::summarize)
                .sorted(Comparator.comparing(ConfigSummary::configType).thenComparing(ConfigSummary::configKey))
                .toList();
    }

    /** All versions of a key, newest first. */
    public List<AssistantConfig> versions(String configKey) {
        List<AssistantConfig> versions = repository.findByConfigKeyOrderByVersionDesc(configKey);
        if (versions.isEmpty()) {
            throw notFound();
        }
        return versions;
    }

    public AssistantConfig version(String configKey, int version) {
        return repository.findByConfigKeyAndVersion(configKey, version).orElseThrow(ConfigService::notFound);
    }

    public List<ValidationError> validateWorkingCopy(String configKey) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        return validator.validate(wc.getConfigType(), wc.getName(), wc.getContent());
    }

    private boolean publishScheduled(AssistantConfig due, Instant now, TransactionStatus status) {
        archivePublished(due.getConfigKey());
        Query claim = Query.query(where("id").is(due.getId())
                .and("status").is(ConfigStatus.SCHEDULED)
                .and("docVersion").is(due.getDocVersion()));
        Update publish = new Update()
                .set("status", ConfigStatus.PUBLISHED)
                .set("updatedAt", now)
                .inc("docVersion", 1L);
        if (mongo.updateFirst(claim, publish, AssistantConfig.class).getModifiedCount() == 1) {
            return true;
        }
        // Changed since it was read (saved, applied over, or published by another instance): undo the archive.
        status.setRollbackOnly();
        return false;
    }

    /**
     * Targeted update of the status only: the archived version keeps its updatedBy / updatedAt (= when it
     * went live). Must run inside the publishing transaction.
     */
    private void archivePublished(String configKey) {
        repository.findByConfigKeyAndStatus(configKey, ConfigStatus.PUBLISHED).ifPresent(old -> {
            Query current = Query.query(where("id").is(old.getId())
                    .and("status").is(ConfigStatus.PUBLISHED)
                    .and("docVersion").is(old.getDocVersion()));
            Update archive = new Update().set("status", ConfigStatus.ARCHIVED).inc("docVersion", 1L);
            if (mongo.updateFirst(current, archive, AssistantConfig.class).getModifiedCount() != 1) {
                throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
            }
        });
    }

    /** The DRAFT or SCHEDULED version of the key, if any. */
    public Optional<AssistantConfig> findWorkingCopy(String configKey) {
        return workingCopy(configKey);
    }

    private Optional<AssistantConfig> workingCopy(String configKey) {
        return repository.findByConfigKeyAndStatusIn(configKey, WORKING_COPY).stream().findFirst();
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
        if (repository.findTopByConfigKeyOrderByVersionDesc(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        throw notFound();
    }

    private static void requireDocVersion(AssistantConfig config, Long expected) {
        if (!Objects.equals(config.getDocVersion(), expected)) {
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
        return repository.findTopByConfigKeyOrderByVersionDesc(configKey).map(c -> c.getVersion() + 1).orElse(1);
    }

    private static Map<String, Object> pendingDetail(AssistantConfig wc) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", wc.getStatus().name());
        detail.put("scheduledAt", wc.getScheduledAt());
        detail.put("updatedAt", wc.getUpdatedAt());
        detail.put("docVersion", wc.getDocVersion());
        return detail;
    }

    private static ConfigSummary summarize(List<AssistantConfig> versions) {
        AssistantConfig latest = versions.stream().max(Comparator.comparingInt(AssistantConfig::getVersion))
                .orElseThrow();
        AssistantConfig published = versions.stream()
                .filter(c -> c.getStatus() == ConfigStatus.PUBLISHED).findFirst().orElse(null);
        AssistantConfig wc = versions.stream()
                .filter(c -> WORKING_COPY.contains(c.getStatus())).findFirst().orElse(null);
        String name = published != null ? published.getName() : wc != null ? wc.getName() : latest.getName();
        return new ConfigSummary(latest.getConfigKey(), latest.getConfigType(), name,
                ConfigSummary.VersionInfo.of(published), ConfigSummary.VersionInfo.of(wc));
    }

    private static AssistantConfig newVersion(String configKey, ConfigType type, String name, String content,
                                              int version, String actor, Instant now) {
        AssistantConfig c = new AssistantConfig();
        c.setConfigKey(configKey);
        c.setConfigType(type);
        c.setName(name);
        c.setContent(content);
        c.setStatus(ConfigStatus.DRAFT);
        c.setVersion(version);
        c.setCreatedBy(actor);
        c.setCreatedAt(now);
        touch(c, actor, now);
        return c;
    }

    private static void touch(AssistantConfig c, String actor, Instant now) {
        c.setUpdatedBy(actor);
        c.setUpdatedAt(now);
    }

    /**
     * Lost optimistic locks, unique-index violations and transaction write conflicts all mean "reload".
     * Spring Data MongoDB translates a WriteConflict (112) into a DataIntegrityViolationException and a failed
     * commit into a TransactionSystemException, so those are mapped only when the Mongo cause is a conflict;
     * anything else (e.g. 121 document validation failure) keeps propagating.
     */
    private static <T> T write(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (OptimisticLockingFailureException | DuplicateKeyException | TransientClientSessionException e) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        } catch (DataIntegrityViolationException | TransactionSystemException e) {
            if (isMongoConflict(e)) {
                throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
            }
            throw e;
        }
    }

    /** Log-level decision only (the HTTP mapping is write()): code 112, duplicate key or an explicit conflict. */
    static boolean isLostRace(RuntimeException e) {
        if (e instanceof AssistantException a) {
            return a.code() == ErrorCode.AI_CONFIG_CONFLICT;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MongoException m && (m.getCode() == WRITE_CONFLICT || m.getCode() == DUPLICATE_KEY)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMongoConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MongoException m && (m.getCode() == WRITE_CONFLICT || m.getCode() == DUPLICATE_KEY
                    || m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || m.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
                return true;
            }
        }
        return false;
    }

    private static AssistantException notFound() {
        return new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
    }

    private static AssistantException invalidInput(String field) {
        return new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(Map.of("field", field)));
    }
}
