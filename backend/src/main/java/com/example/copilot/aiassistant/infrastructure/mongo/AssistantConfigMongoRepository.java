package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Spring Data access to the config collection; only {@link AssistantConfigRepositoryImpl} uses it in main code. */
public interface AssistantConfigMongoRepository extends MongoRepository<AssistantConfigDocument, String> {

    Optional<AssistantConfigDocument> findByConfigKeyAndStatus(String key, ConfigStatus status);

    List<AssistantConfigDocument> findByConfigKeyAndStatusIn(String key, Collection<ConfigStatus> statuses);

    List<AssistantConfigDocument> findByConfigKeyOrderByVersionDesc(String key);

    Optional<AssistantConfigDocument> findByConfigKeyAndVersion(String key, int version);

    Optional<AssistantConfigDocument> findTopByConfigKeyOrderByVersionDesc(String key);

    List<AssistantConfigDocument> findByStatus(ConfigStatus status);

    List<AssistantConfigDocument> findByStatusAndScheduledAtLessThanEqual(ConfigStatus status, Instant time);
}
