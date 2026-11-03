package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.application.config.ConfigQueryRepository;
import com.example.copilot.aiassistant.application.config.ConfigSummary;
import com.example.copilot.aiassistant.application.config.VersionHistoryEntry;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** MongoDB read models of the config screens; queries exclude {@code content}. */
@Repository
public class ConfigQueryRepositoryImpl implements ConfigQueryRepository {

    private final MongoTemplate mongo;

    public ConfigQueryRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<ConfigSummary> listSummaries() {
        return mongo.find(summariesQuery(), AssistantConfigDocument.class).stream()
                .map(AssistantConfigMapper::toDomain)
                .collect(Collectors.groupingBy(AssistantConfig::configKey))
                .values().stream()
                .map(ConfigSummary::of)
                .sorted(Comparator.comparing(ConfigSummary::configType).thenComparing(ConfigSummary::configKey))
                .toList();
    }

    @Override
    public List<VersionHistoryEntry> versionHistory(String configKey) {
        return mongo.find(historyQuery(configKey), AssistantConfigDocument.class).stream()
                .map(AssistantConfigMapper::toDomain)
                .map(c -> new VersionHistoryEntry(c.version(), c.status(), c.scheduledAt(), c.changeNote(),
                        c.basedOnVersion(), c.createdBy(), c.updatedBy(), c.updatedAt()))
                .toList();
    }

    static Query summariesQuery() {
        Query query = new Query();
        query.fields().exclude("content");
        return query;
    }

    static Query historyQuery(String configKey) {
        Query query = Query.query(where("configKey").is(configKey)).with(Sort.by(Sort.Direction.DESC, "version"));
        query.fields().exclude("content");
        return query;
    }
}
