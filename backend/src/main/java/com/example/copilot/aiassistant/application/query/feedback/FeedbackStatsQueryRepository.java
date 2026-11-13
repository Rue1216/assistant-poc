package com.example.copilot.aiassistant.application.query.feedback;

import com.example.copilot.aiassistant.domain.config.ConfigKey;
import java.util.List;

/** Read side of the feedback statistics. */
public interface FeedbackStatsQueryRepository {

    /**
     * Counts per version of the key, newest version first; versions without any rating are absent.
     * With {@code routedOnly} only answers whose route source is ROUTER or CHIP are counted.
     */
    List<VersionFeedback> versionStats(ConfigKey configKey, boolean routedOnly);
}
