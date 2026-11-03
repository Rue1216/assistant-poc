package com.example.copilot.aiassistant.application.feedback;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.domain.repository.AuditRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/** Thumbs ratings stored on the audit document, and their per-version statistics (spec 3.2, 3.3). */
@Service
public class FeedbackService {

    private final AuditRepository audits;
    private final FeedbackStatsQueryRepository stats;
    private final AssistantConfigRepository configs;
    private final Clock clock;

    public FeedbackService(AuditRepository audits, FeedbackStatsQueryRepository stats,
                           AssistantConfigRepository configs, Clock clock) {
        this.audits = audits;
        this.stats = stats;
        this.configs = configs;
        this.clock = clock;
    }

    /**
     * Sets or (null rating) clears the user's rating. Another user's answer, a failed answer and an unknown
     * request id are indistinguishable. When a request id repeats, the newest document is updated.
     */
    public void rate(String requestId, String userId, FeedbackRating rating) {
        if (!audits.setFeedback(requestId, userId, rating, clock.instant())) {
            throw new AssistantException(ErrorCode.AI_FEEDBACK_NOT_FOUND);
        }
    }

    /**
     * Counts per version of the given key, newest version first. Versions without any rating are absent.
     * An instruction counts only answers it actually shaped (routed or chip-selected); fallback answers and
     * legacy records without a route source are excluded. The system prompt counts every answer.
     */
    public List<VersionFeedback> stats(String configKey) {
        AssistantConfig latest = configs.findLatest(configKey)
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CONFIG_NOT_FOUND));
        return stats.versionStats(configKey, latest.configType() == ConfigType.SYSTEM_INSTRUCTION);
    }
}
