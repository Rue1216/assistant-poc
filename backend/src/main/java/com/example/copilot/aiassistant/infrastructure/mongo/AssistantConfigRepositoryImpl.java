package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.mongodb.MongoException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MongoDB implementation of {@link AssistantConfigRepository}. Every update is conditional on {@code docVersion}
 * (Spring Data {@code @Version} or an explicit filter); publishing and applying run in a Mongo transaction, so
 * there is at most one PUBLISHED and one working copy per key. No operation deletes a document.
 */
@Repository
public class AssistantConfigRepositoryImpl implements AssistantConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(AssistantConfigRepositoryImpl.class);

    private static final List<ConfigStatus> WORKING_COPY = List.of(ConfigStatus.DRAFT, ConfigStatus.SCHEDULED);
    private static final int WRITE_CONFLICT = 112;
    private static final int DUPLICATE_KEY = 11000;

    private final AssistantConfigMongoRepository mongoRepository;
    private final MongoTemplate mongo;
    private final TransactionTemplate tx;

    public AssistantConfigRepositoryImpl(AssistantConfigMongoRepository mongoRepository, MongoTemplate mongo,
                                         MongoTransactionManager transactionManager) {
        this.mongoRepository = mongoRepository;
        this.mongo = mongo;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<AssistantConfig> findPublished(String configKey) {
        return mongoRepository.findByConfigKeyAndStatus(configKey, ConfigStatus.PUBLISHED)
                .map(AssistantConfigMapper::toDomain);
    }

    @Override
    public Optional<AssistantConfig> findWorkingCopy(String configKey) {
        return mongoRepository.findByConfigKeyAndStatusIn(configKey, WORKING_COPY).stream().findFirst()
                .map(AssistantConfigMapper::toDomain);
    }

    @Override
    public Optional<AssistantConfig> findLatest(String configKey) {
        return mongoRepository.findTopByConfigKeyOrderByVersionDesc(configKey).map(AssistantConfigMapper::toDomain);
    }

    @Override
    public Optional<AssistantConfig> findVersion(String configKey, int version) {
        return mongoRepository.findByConfigKeyAndVersion(configKey, version).map(AssistantConfigMapper::toDomain);
    }

    @Override
    public List<AssistantConfig> findPublished() {
        return toDomain(mongoRepository.findByStatus(ConfigStatus.PUBLISHED));
    }

    @Override
    public List<AssistantConfig> findScheduledDue(Instant now) {
        return toDomain(mongoRepository.findByStatusAndScheduledAtLessThanEqual(ConfigStatus.SCHEDULED, now));
    }

    @Override
    public boolean existsByConfigType(ConfigType type) {
        return mongoRepository.existsByConfigType(type);
    }

    @Override
    public long count() {
        return mongoRepository.count();
    }

    @Override
    public List<AssistantConfig> findAllWithoutContent() {
        Query withoutContent = new Query();
        withoutContent.fields().exclude("content");
        return toDomain(mongo.find(withoutContent, AssistantConfigDocument.class));
    }

    @Override
    public AssistantConfig insert(AssistantConfig config) {
        return write(() -> AssistantConfigMapper.toDomain(
                mongoRepository.insert(AssistantConfigMapper.toDocument(config))));
    }

    @Override
    public AssistantConfig save(AssistantConfig config) {
        return write(() -> AssistantConfigMapper.toDomain(
                mongoRepository.save(AssistantConfigMapper.toDocument(config))));
    }

    @Override
    public AssistantConfig publish(AssistantConfig workingCopy) {
        return write(() -> tx.execute(status -> {
            archivePublished(workingCopy.configKey());
            return AssistantConfigMapper.toDomain(mongoRepository.save(AssistantConfigMapper.toDocument(workingCopy)));
        }));
    }

    @Override
    public AssistantConfig replaceWorkingCopy(AssistantConfig discarded, AssistantConfig draft) {
        return write(() -> tx.execute(status -> {
            if (discarded != null) {
                mongoRepository.save(AssistantConfigMapper.toDocument(discarded));
            }
            return AssistantConfigMapper.toDomain(mongoRepository.insert(AssistantConfigMapper.toDocument(draft)));
        }));
    }

    /**
     * Claims {@code due} with a conditional update (still SCHEDULED, same docVersion) so that concurrent runs
     * publish it once; {@code updatedBy} stays the person who scheduled it. A lost race returns false.
     */
    @Override
    public boolean publishScheduled(AssistantConfig due, Instant now) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> claim(due, now, status)));
        } catch (RuntimeException e) {
            if (isLostRace(e)) {
                // Expected when several instances run the job: the other one won.
                log.debug("scheduled publish lost race configKey={} version={}", due.configKey(), due.version());
                return false;
            }
            throw e;
        }
    }

    private boolean claim(AssistantConfig due, Instant now, TransactionStatus status) {
        archivePublished(due.configKey());
        Query claim = Query.query(where("id").is(due.id())
                .and("status").is(ConfigStatus.SCHEDULED)
                .and("docVersion").is(due.docVersion()));
        Update publish = new Update()
                .set("status", ConfigStatus.PUBLISHED)
                .set("updatedAt", now)
                .inc("docVersion", 1L);
        if (mongo.updateFirst(claim, publish, AssistantConfigDocument.class).getModifiedCount() == 1) {
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
        mongoRepository.findByConfigKeyAndStatus(configKey, ConfigStatus.PUBLISHED).ifPresent(old -> {
            Query current = Query.query(where("id").is(old.getId())
                    .and("status").is(ConfigStatus.PUBLISHED)
                    .and("docVersion").is(old.getDocVersion()));
            Update archive = new Update().set("status", ConfigStatus.ARCHIVED).inc("docVersion", 1L);
            if (mongo.updateFirst(current, archive, AssistantConfigDocument.class).getModifiedCount() != 1) {
                throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
            }
        });
    }

    private static List<AssistantConfig> toDomain(List<AssistantConfigDocument> docs) {
        return docs.stream().map(AssistantConfigMapper::toDomain).toList();
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

    /** Log-level decision of the scheduler only (the HTTP mapping is write()): code 112, duplicate key or a conflict. */
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
}
