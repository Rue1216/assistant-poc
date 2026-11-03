package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.application.feedback.FeedbackStatsQueryRepository;
import com.example.copilot.aiassistant.application.feedback.VersionFeedback;
import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject;
import org.springframework.stereotype.Repository;

/** MongoDB aggregation over the audit collection. */
@Repository
public class FeedbackStatsQueryRepositoryImpl implements FeedbackStatsQueryRepository {

    private final MongoTemplate mongo;

    public FeedbackStatsQueryRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<VersionFeedback> versionStats(String configKey, boolean routedOnly) {
        // The leading predicates stay as they were so the partial feedback index remains usable.
        Criteria first = Criteria.where("feedback").type(JsonSchemaObject.Type.STRING)
                .and("promptVersions").elemMatch(Criteria.where("configKey").is(configKey));
        if (routedOnly) {
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
