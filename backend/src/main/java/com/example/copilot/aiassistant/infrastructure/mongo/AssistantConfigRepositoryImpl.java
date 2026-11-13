package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.TransientClientSessionException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionSystemException;

/**
 * MongoDB implementation of {@link AssistantConfigRepository}. Every update is conditional on {@code docVersion}
 * (Spring Data {@code @Version} or an explicit filter). Publishing and applying write several documents and rely on
 * the caller's transaction ({@code @Transactional} on the use case), so there is at most one PUBLISHED and one
 * working copy per key. No operation deletes a document.
 */
@Repository
public class AssistantConfigRepositoryImpl implements AssistantConfigRepository {

    private static final List<ConfigStatus> WORKING_COPY = List.of(ConfigStatus.DRAFT, ConfigStatus.SCHEDULED);

    private final MongoTemplate mongo;
    private final AssistantConfigDocumentMapper mapper;

    public AssistantConfigRepositoryImpl(MongoTemplate mongo, AssistantConfigDocumentMapper mapper) {
        this.mongo = mongo;
        this.mapper = mapper;
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
        return write(() -> {
            archivePublished(workingCopy.configKey());
            return mapper.toDomain(mongo.save(mapper.toDocument(workingCopy)));
        });
    }

    @Override
    public AssistantConfig replaceWorkingCopy(AssistantConfig discarded, AssistantConfig draft) {
        return write(() -> {
            if (discarded != null) {
                mongo.save(mapper.toDocument(discarded));
            }
            return mapper.toDomain(mongo.insert(mapper.toDocument(draft)));
        });
    }

    /**
     * Claims {@code due} with a conditional update (still SCHEDULED, same docVersion) so that concurrent runs
     * publish it once; {@code updatedBy} stays the person who scheduled it. Mongo failures are not mapped here:
     * the use-case boundary translates conflicts and the scheduler tells a lost race from a real failure.
     */
    @Override
    public void publishScheduled(AssistantConfig due, Instant now) {
        archivePublished(due.configKey());
        Query claim = Query.query(where("id").is(due.id())
                .and("status").is(ConfigStatus.SCHEDULED)
                .and("docVersion").is(due.docVersion()));
        Update publish = new Update()
                .set("status", ConfigStatus.PUBLISHED)
                .set("updatedAt", now)
                .inc("docVersion", 1L);
        if (mongo.updateFirst(claim, publish, AssistantConfigDocument.class).getModifiedCount() != 1) {
            // Changed since it was read (saved, applied over, or published by another instance): the caller's
            // transaction rolls the archive back.
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
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

    /**
     * Targeted update of the status only: the archived version keeps its updatedBy / updatedAt (= when it
     * went live). Must run inside the caller's publishing transaction.
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
     * Lost optimistic locks, unique-index violations and transaction write conflicts at write time all mean
     * "reload". Spring Data MongoDB translates a WriteConflict (112) into a DataIntegrityViolationException, so
     * those are mapped only when the Mongo cause is a conflict; anything else (e.g. 121 document validation
     * failure) keeps propagating. A failure of the commit itself surfaces in the caller's transaction proxy and
     * is mapped by {@link ConfigTransactionConflictAspect}.
     */
    private static <T> T write(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (OptimisticLockingFailureException | DuplicateKeyException | TransientClientSessionException e) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        } catch (DataIntegrityViolationException | TransactionSystemException e) {
            if (MongoConflicts.isConflict(e)) {
                throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
            }
            throw e;
        }
    }
}
