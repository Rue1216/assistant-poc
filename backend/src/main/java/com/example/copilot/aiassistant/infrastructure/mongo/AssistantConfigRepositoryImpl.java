package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
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
@Slf4j
public class AssistantConfigRepositoryImpl implements AssistantConfigRepository {

    private static final List<ConfigStatus> WORKING_COPY = List.of(ConfigStatus.DRAFT, ConfigStatus.SCHEDULED);
    private static final int WRITE_CONFLICT = 112;
    private static final int DUPLICATE_KEY = 11000;

    private final MongoTemplate mongo;
    private final AssistantConfigDocumentMapper mapper;
    private final TransactionTemplate tx;

    public AssistantConfigRepositoryImpl(MongoTemplate mongo, AssistantConfigDocumentMapper mapper,
                                         MongoTransactionManager transactionManager) {
        this.mongo = mongo;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<AssistantConfig> findPublished(ConfigKey configKey) {
        return Optional.ofNullable(mongo.findOne(byKeyAndStatus(configKey, ConfigStatus.PUBLISHED),
                AssistantConfigDocument.class)).map(mapper::toDomain);
    }

    @Override
    public Optional<AssistantConfig> findWorkingCopy(ConfigKey configKey) {
        Query query = Query.query(where("configKey").is(configKey.value()).and("status").in(WORKING_COPY));
        return mongo.find(query, AssistantConfigDocument.class).stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public Optional<AssistantConfig> findLatest(ConfigKey configKey) {
        Query query = Query.query(where("configKey").is(configKey.value()))
                .with(Sort.by(Sort.Direction.DESC, "version")).limit(1);
        return Optional.ofNullable(mongo.findOne(query, AssistantConfigDocument.class)).map(mapper::toDomain);
    }

    @Override
    public Optional<AssistantConfig> findVersion(ConfigKey configKey, int version) {
        Query query = Query.query(where("configKey").is(configKey.value()).and("version").is(version));
        return Optional.ofNullable(mongo.findOne(query, AssistantConfigDocument.class)).map(mapper::toDomain);
    }

    @Override
    public List<AssistantConfig> findPublished() {
        return toDomain(mongo.find(Query.query(where("status").is(ConfigStatus.PUBLISHED)),
                AssistantConfigDocument.class));
    }

    @Override
    public List<AssistantConfig> findScheduledDue(Instant now) {
        Query query = Query.query(where("status").is(ConfigStatus.SCHEDULED).and("scheduledAt").lte(now));
        return toDomain(mongo.find(query, AssistantConfigDocument.class));
    }

    @Override
    public long count() {
        return mongo.count(new Query(), AssistantConfigDocument.class);
    }

    @Override
    public AssistantConfig insert(AssistantConfig config) {
        return write(() -> mapper.toDomain(mongo.insert(mapper.toDocument(config))));
    }

    @Override
    public AssistantConfig save(AssistantConfig config) {
        return write(() -> mapper.toDomain(mongo.save(mapper.toDocument(config))));
    }

    @Override
    public AssistantConfig publish(AssistantConfig workingCopy) {
        return write(() -> tx.execute(status -> {
            archivePublished(workingCopy.configKey());
            return mapper.toDomain(mongo.save(mapper.toDocument(workingCopy)));
        }));
    }

    @Override
    public AssistantConfig replaceWorkingCopy(AssistantConfig discarded, AssistantConfig draft) {
        return write(() -> tx.execute(status -> {
            if (discarded != null) {
                mongo.save(mapper.toDocument(discarded));
            }
            return mapper.toDomain(mongo.insert(mapper.toDocument(draft)));
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

    @Override
    public boolean revertScheduled(AssistantConfig due, String actor, Instant now) {
        Query current = Query.query(where("id").is(due.id())
                .and("status").is(ConfigStatus.SCHEDULED)
                .and("docVersion").is(due.docVersion()));
        Update revert = new Update()
                .set("status", ConfigStatus.DRAFT)
                .unset("scheduledAt")
                .set("updatedBy", actor)
                .set("updatedAt", now)
                .inc("docVersion", 1L);
        return mongo.updateFirst(current, revert, AssistantConfigDocument.class).getModifiedCount() == 1;
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
    private void archivePublished(ConfigKey configKey) {
        Optional.ofNullable(mongo.findOne(byKeyAndStatus(configKey, ConfigStatus.PUBLISHED), AssistantConfigDocument.class)).ifPresent(old -> {
            Query current = Query.query(where("id").is(old.getId())
                    .and("status").is(ConfigStatus.PUBLISHED)
                    .and("docVersion").is(old.getDocVersion()));
            Update archive = new Update().set("status", ConfigStatus.ARCHIVED).inc("docVersion", 1L);
            if (mongo.updateFirst(current, archive, AssistantConfigDocument.class).getModifiedCount() != 1) {
                throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
            }
        });
    }

    private static Query byKeyAndStatus(ConfigKey configKey, ConfigStatus status) {
        return Query.query(where("configKey").is(configKey.value()).and("status").is(status));
    }

    private List<AssistantConfig> toDomain(List<AssistantConfigDocument> docs) {
        return docs.stream().map(mapper::toDomain).toList();
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
