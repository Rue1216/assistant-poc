package com.example.copilot.aiassistant.infrastructure.scheduler;

import com.example.copilot.aiassistant.application.config.ConfigCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes scheduled prompt versions once they are due (04 section 2.4). Safe to run on several instances. */
@Component
@ConditionalOnProperty(name = "ai-assistant.config.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduledPublishJob {

    private final ConfigCommandService configService;

    public ScheduledPublishJob(ConfigCommandService configService) {
        this.configService = configService;
    }

    @Scheduled(fixedDelayString = "${ai-assistant.config.schedule-check-interval}")
    void run() {
        try {
            int published = configService.publishDue();
            if (published > 0) {
                log.info("published {} scheduled config version(s)", published);
            }
        } catch (RuntimeException e) {
            log.warn("scheduled publish run failed error={}", e.getClass().getName());
        }
    }
}
