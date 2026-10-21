package com.example.copilot.aiassistant.config;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssistantConfigRepository extends MongoRepository<AssistantConfig, String> {

    Optional<AssistantConfig> findByConfigKeyAndStatus(String key, ConfigStatus status);

    List<AssistantConfig> findByConfigKeyAndStatusIn(String key, Collection<ConfigStatus> statuses);

    List<AssistantConfig> findByConfigKeyOrderByVersionDesc(String key);

    Optional<AssistantConfig> findByConfigKeyAndVersion(String key, int version);

    Optional<AssistantConfig> findTopByConfigKeyOrderByVersionDesc(String key);

    List<AssistantConfig> findByStatus(ConfigStatus status);

    boolean existsByConfigType(ConfigType type);

    List<AssistantConfig> findByStatusAndScheduledAtLessThanEqual(ConfigStatus status, Instant time);
}
