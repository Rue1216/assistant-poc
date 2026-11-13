package com.example.copilot.aiassistant.application.query.config;

import com.example.copilot.aiassistant.domain.config.ConfigKey;
import java.util.List;

/** Read models of the admin config screens; none of them reads {@code content}. */
public interface ConfigQueryRepository {

    /** One summary per config key, ordered by configType then configKey. */
    List<ConfigSummary> listSummaries();

    /** Versions of the key, highest version first; empty when the key is unknown. */
    List<VersionHistoryEntry> versionHistory(ConfigKey configKey);
}
