package com.example.copilot.aiassistant.application.query.config;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.config.ValidationError;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read side of the prompt config admin screens. */
@Service
public class ConfigQueryService {

    private final ConfigQueryRepository queries;
    private final AssistantConfigRepository repository;
    private final ConfigValidator validator;

    public ConfigQueryService(ConfigQueryRepository queries, AssistantConfigRepository repository,
                              ConfigValidator validator) {
        this.queries = queries;
        this.repository = repository;
        this.validator = validator;
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

    /** Runs the validation rules over the DRAFT or SCHEDULED version without changing it. */
    public List<ValidationError> validateWorkingCopy(ConfigKey configKey) {
        AssistantConfig wc = existingWorkingCopy(configKey);
        return validator.validate(wc.configType(), wc.name(), wc.changeNote(), wc.content());
    }

    /** The DRAFT or SCHEDULED version of the key, if any. */
    public Optional<AssistantConfig> findWorkingCopy(ConfigKey configKey) {
        return repository.findWorkingCopy(configKey);
    }

    /**
     * An unknown key is NOT_FOUND; a known key without a DRAFT/SCHEDULED copy means the copy the caller saw
     * was published, discarded or applied over by someone else in the meantime, which is a CONFLICT.
     */
    private AssistantConfig existingWorkingCopy(ConfigKey configKey) {
        Optional<AssistantConfig> wc = repository.findWorkingCopy(configKey);
        if (wc.isPresent()) {
            return wc.get();
        }
        if (repository.findLatest(configKey).isPresent()) {
            throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
        }
        throw notFound();
    }

    private static AssistantException notFound() {
        return new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND);
    }
}
