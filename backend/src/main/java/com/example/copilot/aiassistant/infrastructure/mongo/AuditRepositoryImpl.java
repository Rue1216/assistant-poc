package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import com.example.copilot.aiassistant.domain.audit.TraceId;
import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;
import com.example.copilot.aiassistant.domain.repository.AuditRepository;
import java.time.Instant;
import java.util.Date;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** MongoDB implementation of {@link AuditRepository}. */
@Repository
public class AuditRepositoryImpl implements AuditRepository {

    private final MongoTemplate mongo;
    private final AuditRecordDocumentMapper mapper;

    public AuditRepositoryImpl(MongoTemplate mongo, AuditRecordDocumentMapper mapper) {
        this.mapper = mapper;
        this.mongo = mongo;
    }

    @Override
    public void insert(AuditRecord record) {
        mongo.insert(mapper.toDocument(record));
    }

    @Override
    public boolean setFeedback(TraceId traceId, String userId, FeedbackRating rating, Instant at) {
        Query query = new Query(Criteria.where("traceId").is(traceId.value()).and("userId").is(userId)
                .and("outcome").is("ANSWERED")).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        Update update = new Update().set("feedback", rating == null ? null : rating.name())
                .set("feedbackAt", Date.from(at));
        return mongo.findAndModify(query, update, AuditRecordDocument.class) != null;
    }
}
