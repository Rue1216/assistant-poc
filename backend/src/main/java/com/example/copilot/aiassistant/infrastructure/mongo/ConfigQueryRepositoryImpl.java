package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.application.query.config.ConfigQueryRepository;
import com.example.copilot.aiassistant.application.query.config.ConfigSummary;
import com.example.copilot.aiassistant.application.query.config.VersionHistoryEntry;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** MongoDB read models of the config screens; queries exclude {@code content}. */
@Repository
public class ConfigQueryRepositoryImpl implements ConfigQueryRepository {

    private static final String[] SUMMARY_FIELDS = {"configKey", "configType", "name", "status", "version",
            "scheduledAt", "updatedBy", "updatedAt"};

    private final MongoTemplate mongo;

    public ConfigQueryRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Cost depends on the number of keys, not on accumulated versions: distinct keys, then only the
     * PUBLISHED/DRAFT/SCHEDULED versions, then one latest version for each key that has none of those.
     */
    @Override
    public List<ConfigSummary> listSummaries() {
        List<String> keys = mongo.findDistinct(new Query(), "configKey", AssistantConfigDocument.class, String.class);
        Map<String, List<AssistantConfigDocument>> byKey = mongo.find(activeVersionsQuery(), AssistantConfigDocument.class)
                .stream()
                .collect(Collectors.groupingBy(AssistantConfigDocument::getConfigKey));
        List<List<AssistantConfigDocument>> groups = new ArrayList<>(byKey.values());
        for (String key : keys) {
            if (!byKey.containsKey(key)) {
                AssistantConfigDocument latest = mongo.findOne(latestVersionQuery(key), AssistantConfigDocument.class);
                if (latest != null) {
                    groups.add(List.of(latest));
                }
            }
        }
        return groups.stream()
                .map(ConfigQueryRepositoryImpl::summary)
                .sorted(Comparator.comparing(ConfigSummary::configType).thenComparing(ConfigSummary::configKey))
                .toList();
    }

    @Override
    public List<VersionHistoryEntry> versionHistory(ConfigKey configKey) {
        return mongo.find(historyQuery(configKey.value()), AssistantConfigDocument.class).stream()
                .map(d -> new VersionHistoryEntry(d.getVersion(), d.getStatus(), d.getScheduledAt(), d.getChangeNote(),
                        d.getBasedOnVersion(), d.getCreatedBy(), d.getUpdatedBy(), d.getUpdatedAt()))
                .toList();
    }

    /** The name is the published one, else the working copy's, else the latest's. */
    private static ConfigSummary summary(List<AssistantConfigDocument> versions) {
        AssistantConfigDocument latest = versions.stream()
                .max(Comparator.comparingInt(AssistantConfigDocument::getVersion)).orElseThrow();
        AssistantConfigDocument published = versions.stream()
                .filter(d -> d.getStatus() == ConfigStatus.PUBLISHED).findFirst().orElse(null);
        AssistantConfigDocument workingCopy = versions.stream()
                .filter(d -> d.getStatus() == ConfigStatus.DRAFT || d.getStatus() == ConfigStatus.SCHEDULED)
                .findFirst().orElse(null);
        String name = published != null ? published.getName()
                : workingCopy != null ? workingCopy.getName() : latest.getName();
        return new ConfigSummary(latest.getConfigKey(), latest.getConfigType(), name, versionInfo(published),
                versionInfo(workingCopy));
    }

    private static ConfigSummary.VersionInfo versionInfo(AssistantConfigDocument d) {
        return d == null ? null : new ConfigSummary.VersionInfo(d.getVersion(), d.getStatus(), d.getScheduledAt(),
                d.getUpdatedBy(), d.getUpdatedAt());
    }

    static Query activeVersionsQuery() {
        Query query = Query.query(where("status").in(
                ConfigStatus.PUBLISHED, ConfigStatus.DRAFT, ConfigStatus.SCHEDULED));
        query.fields().include(SUMMARY_FIELDS);
        return query;
    }

    static Query latestVersionQuery(String configKey) {
        Query query = Query.query(where("configKey").is(configKey))
                .with(Sort.by(Sort.Direction.DESC, "version")).limit(1);
        query.fields().include(SUMMARY_FIELDS);
        return query;
    }

    static Query historyQuery(String configKey) {
        Query query = Query.query(where("configKey").is(configKey)).with(Sort.by(Sort.Direction.DESC, "version"));
        query.fields().exclude("content");
        return query;
    }
}
