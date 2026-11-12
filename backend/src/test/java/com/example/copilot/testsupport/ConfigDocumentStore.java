package com.example.copilot.testsupport;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Test-side access to the config collection through {@link MongoTemplate}; not a Spring bean. */
public class ConfigDocumentStore {

    private final MongoTemplate mongo;

    public ConfigDocumentStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void deleteAll() {
        mongo.remove(new Query(), AssistantConfigDocument.class);
    }

    public List<AssistantConfigDocument> findAll() {
        return mongo.findAll(AssistantConfigDocument.class);
    }

    public Optional<AssistantConfigDocument> findByConfigKeyAndStatus(String key, ConfigStatus status) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(where("configKey").is(key).and("status").is(status)), AssistantConfigDocument.class));
    }

    public List<AssistantConfigDocument> findByConfigKeyOrderByVersionDesc(String key) {
        return mongo.find(Query.query(where("configKey").is(key)).with(Sort.by(Sort.Direction.DESC, "version")),
                AssistantConfigDocument.class);
    }

    public Optional<AssistantConfigDocument> findTopByConfigKeyOrderByVersionDesc(String key) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(where("configKey").is(key)).with(Sort.by(Sort.Direction.DESC, "version")).limit(1),
                AssistantConfigDocument.class));
    }

    public AssistantConfigDocument insert(AssistantConfigDocument document) {
        return mongo.insert(document);
    }

    public AssistantConfigDocument save(AssistantConfigDocument document) {
        return mongo.save(document);
    }
}
