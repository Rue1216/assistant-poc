package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.EvalRunRepository;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * MongoDB implementation of {@link EvalRunRepository}. Reads and inserts go through {@link EvalRunDocument}; every
 * update is conditional on {@code docVersion}, and {@code false} means the run was changed (e.g. marked interrupted)
 * in the meantime.
 */
@Repository
public class EvalRunRepositoryImpl implements EvalRunRepository {

    private static final String RUNNING = EvalStatus.RUNNING.name();

    private final MongoTemplate mongo;
    private final EvalRunDocumentMapper mapper;

    public EvalRunRepositoryImpl(MongoTemplate mongo, EvalRunDocumentMapper mapper) {
        this.mongo = mongo;
        this.mapper = mapper;
    }

    @Override
    public String insert(EvalRun run) {
        try {
            return mongo.insert(mapper.toDocument(run)).getId();
        } catch (DuplicateKeyException e) {
            throw new AssistantException(ErrorCode.AI_EVAL_RUNNING);
        }
    }

    @Override
    public Optional<EvalRun> findById(String id) {
        if (!ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findById(new ObjectId(id), EvalRunDocument.class)).map(mapper::toDomain);
    }

    @Override
    public Optional<EvalRun> findRunning() {
        return Optional.ofNullable(mongo.findOne(Query.query(where("status").is(RUNNING)), EvalRunDocument.class))
                .map(mapper::toDomain);
    }

    @Override
    public List<EvalRun> recent(ConfigKey configKey, int limit) {
        Query query = Query.query(where("configKey").is(configKey.value())).with(Sort.by(Sort.Direction.DESC, "startedAt"))
                .limit(limit);
        query.fields().exclude("cases");
        return mongo.find(query, EvalRunDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean appendCase(String id, long docVersion, EvalCaseRecord c, Instant now) {
        Update update = new Update().push("cases", mapper.toDocument(c)).inc("completedCases", 1)
                .set("heartbeatAt", Date.from(now));
        return update(id, docVersion, update);
    }

    @Override
    public boolean touch(String id, long docVersion, Instant now) {
        return update(id, docVersion, new Update().set("heartbeatAt", Date.from(now)));
    }

    @Override
    public boolean complete(String id, long docVersion, EvalSummary s, Instant now) {
        Update update = new Update().set("status", EvalStatus.COMPLETED.name()).set("summary", mapper.toDocument(s))
                .set("finishedAt", Date.from(now));
        return update(id, docVersion, update);
    }

    @Override
    public boolean fail(String id, long docVersion, String errorCode, Instant now) {
        Update update = new Update().set("status", EvalStatus.FAILED.name()).set("errorCode", errorCode)
                .set("finishedAt", Date.from(now));
        return update(id, docVersion, update);
    }

    private boolean update(String id, long docVersion, Update update) {
        if (!ObjectId.isValid(id)) {
            return false;
        }
        Query query = Query.query(where("_id").is(new ObjectId(id)).and("docVersion").is(docVersion)
                .and("status").is(RUNNING));
        update.inc("docVersion", 1L);
        return mongo.updateFirst(query, update, EvalRunDocument.class).getModifiedCount() == 1;
    }
}
