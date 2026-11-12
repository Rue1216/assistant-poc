package com.example.copilot.aiassistant.application.config;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read side of the prompt config admin screens. */
@Service
public class ConfigQueryService {

    private final ConfigQueryRepository queries;
    private final AssistantConfigRepository repository;

    public ConfigQueryService(ConfigQueryRepository queries, AssistantConfigRepository repository) {
        this.queries = queries;
        this.repository = repository;
    }

    public List<ConfigSummary> list() {
        return queries.listSummaries();
    }

    /** All versions of a key, newest first, without their content (the history view does not show it). */
    public List<VersionHistoryEntry> versions(ConfigKey configKey) {
        List<VersionHistoryEntry> history = queries.versionHistory(configKey);
        if (history.isEmpty()) {
            throw notFound();
        }
        return history;
    }

    public AssistantConfig version(ConfigKey configKey, int version) {
        return repository.findVersion(configKey, version).orElseThrow(ConfigQueryService::notFound);
    }

    private static AssistantException notFound() {
        return new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
    }
}
