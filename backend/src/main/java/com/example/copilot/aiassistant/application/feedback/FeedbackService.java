package com.example.copilot.aiassistant.application.feedback;

import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject;
import org.springframework.stereotype.Service;

/** Thumbs ratings stored on the audit document, and their per-version statistics (spec 3.2, 3.3). */
@Service
public class FeedbackService {

    private final MongoTemplate mongo;
    private final AssistantConfigRepository configs;
    private final Clock clock;

    public FeedbackService(MongoTemplate mongo, AssistantConfigRepository configs, Clock clock) {
        this.mongo = mongo;
        this.configs = configs;
        this.clock = clock;
    }

    /**
     * Sets or (null rating) clears the user's rating. Another user's answer, a failed answer and an unknown
     * request id are indistinguishable. When a request id repeats, the newest document is updated.
     */
    public void rate(String requestId, String userId, FeedbackRating rating) {
        Query query = new Query(Criteria.where("requestId").is(requestId).and("userId").is(userId)
                .and("outcome").is("ANSWERED")).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        Update update = new Update().set("feedback", rating == null ? null : rating.name())
                .set("feedbackAt", Date.from(clock.instant()));
        if (mongo.findAndModify(query, update, Document.class, AuditRecord.COLLECTION) == null) {
            throw new AssistantException(ErrorCode.AI_FEEDBACK_NOT_FOUND);
        }
    }

    /**
     * Counts per version of the given key, newest version first. Versions without any rating are absent.
     * An instruction counts only answers it actually shaped (routed or chip-selected); fallback answers and
     * legacy records without a route source are excluded. The system prompt counts every answer.
     */
    public List<VersionFeedback> stats(String configKey) {
        AssistantConfig latest = configs.findTopByConfigKeyOrderByVersionDesc(configKey)
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND));
        // The leading predicates stay as they were so the partial feedback index remains usable.
        Criteria first = Criteria.where("feedback").type(JsonSchemaObject.Type.STRING)
                .and("promptVersions").elemMatch(Criteria.where("configKey").is(configKey));
        if (latest.getConfigType() == ConfigType.SYSTEM_INSTRUCTION) {
            first = first.and("routeSource").in("ROUTER", "CHIP");
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(first),
                Aggregation.unwind("promptVersions"),
                Aggregation.match(Criteria.where("promptVersions.configKey").is(configKey)),
                Aggregation.group("promptVersions.version")
                        .sum(ConditionalOperators.when(Criteria.where("feedback").is("UP")).then(1).otherwise(0))
                        .as("up")
                        .sum(ConditionalOperators.when(Criteria.where("feedback").is("DOWN")).then(1).otherwise(0))
                        .as("down"),
                Aggregation.sort(Sort.Direction.DESC, "_id"));
        return mongo.aggregate(aggregation, AuditRecord.COLLECTION, Document.class).getMappedResults().stream()
                .map(d -> new VersionFeedback(d.getInteger("_id"), d.get("up", Number.class).longValue(),
                        d.get("down", Number.class).longValue()))
                .toList();
    }
}
