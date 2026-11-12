package com.example.copilot.aiassistant.application.feedback;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Per-version thumbs statistics (spec 3.3). */
@Service
public class FeedbackQueryService {

    private final FeedbackStatsQueryRepository stats;
    private final AssistantConfigRepository configs;

    public FeedbackQueryService(FeedbackStatsQueryRepository stats, AssistantConfigRepository configs) {
        this.stats = stats;
        this.configs = configs;
    }

    /**
     * Counts per version of the given key, newest version first. Versions without any rating are absent.
     * An instruction counts only answers it actually shaped (routed or chip-selected); fallback answers and
     * legacy records without a route source are excluded. The system prompt counts every answer.
     */
    public List<VersionFeedback> stats(ConfigKey configKey) {
        AssistantConfig latest = configs.findLatest(configKey)
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND));
        return stats.versionStats(configKey, latest.configType() == ConfigType.SYSTEM_INSTRUCTION);
    }
}
