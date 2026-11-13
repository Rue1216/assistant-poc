package com.example.copilot.aiassistant.infrastructure.scheduler;

import com.example.copilot.aiassistant.application.command.config.ConfigCommandUseCase;
import com.example.copilot.aiassistant.application.command.config.ScheduledPublishOutcome;
import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.infrastructure.mongo.MongoConflicts;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes scheduled prompt versions once they are due (04 section 2.4). Safe to run on several instances.
 * Each due version is published in its own transaction (a call through the use case's proxy), so one failure
 * does not hold back the others.
 */
@Component
@ConditionalOnProperty(name = "ai-assistant.config.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduledPublishJob {

    private final ConfigCommandUseCase configService;

    public ScheduledPublishJob(ConfigCommandUseCase configService) {
        this.configService = configService;
    }

    @Scheduled(fixedDelayString = "${ai-assistant.config.schedule-check-interval}")
    void run() {
        MDC.put(RequestContext.MDC_KEY, RequestContext.newId());
        MDC.put(RequestContext.SPAN_KEY, RequestContext.newId());
        try {
            int published = 0;
            for (AssistantConfig due : configService.findScheduledDue()) {
                if (publish(due)) {
                    published++;
                }
            }
            if (published > 0) {
                log.info("published {} scheduled config version(s)", published);
            }
        } catch (RuntimeException e) {
            log.warn("scheduled publish run failed error={}", e.getClass().getName());
        } finally {
            MDC.remove(RequestContext.MDC_KEY);
            MDC.remove(RequestContext.SPAN_KEY);
        }
    }

    /** True when {@code due} went live; a failure is logged (without its message) and the run goes on. */
    private boolean publish(AssistantConfig due) {
        try {
            return configService.publishScheduled(due) == ScheduledPublishOutcome.PUBLISHED;
        } catch (RuntimeException e) {
            if (isLostRace(e)) {
                // Expected when several instances run the job, or someone changed the version meanwhile.
                log.debug("scheduled publish lost race configKey={} version={}", due.configKey(), due.version());
            } else {
                log.warn("scheduled publish failed configKey={} version={} error={}",
                        due.configKey(), due.version(), failureType(e));
            }
            return false;
        }
    }

    /**
     * Log-level decision only: another writer (code 112), the unique index (11000) or a stale docVersion won.
     * A conflict translated at the use-case boundary keeps its Mongo failure as the cause; there only a write
     * conflict or a duplicate key counts as a lost race, while a transient or unknown-commit failure is a WARN.
     */
    static boolean isLostRace(RuntimeException e) {
        if (e instanceof AssistantException a) {
            return a.code() == ErrorCode.AI_CONFIG_CONFLICT
                    && (a.getCause() == null || MongoConflicts.isWriteConflictOrDuplicateKey(a.getCause()));
        }
        return MongoConflicts.isWriteConflictOrDuplicateKey(e);
    }

    /** The class of what actually failed: a translated conflict reports its Mongo cause. */
    private static String failureType(RuntimeException e) {
        Throwable failure = e instanceof AssistantException && e.getCause() != null ? e.getCause() : e;
        return failure.getClass().getName();
    }
}
