package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;
import com.example.copilot.aiassistant.domain.repository.AuditRepository;
import java.time.Instant;
import java.util.Date;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** MongoDB implementation of {@link AuditRepository}; documents are written raw so the stored shape is exact. */
@Repository
public class AuditRepositoryImpl implements AuditRepository {

    private final MongoTemplate mongo;

    public AuditRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(AuditRecord record) {
        mongo.insert(AuditRecordMapper.toDocument(record), AuditRecord.COLLECTION);
    }

    @Override
    public boolean setFeedback(String requestId, String userId, FeedbackRating rating, Instant at) {
        Query query = new Query(Criteria.where("requestId").is(requestId).and("userId").is(userId)
                .and("outcome").is("ANSWERED")).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        Update update = new Update().set("feedback", rating == null ? null : rating.name())
                .set("feedbackAt", Date.from(at));
        return mongo.findAndModify(query, update, Document.class, AuditRecord.COLLECTION) != null;
    }
}
