package com.example.copilot.aiassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes scheduled prompt versions once they are due (04 section 2.4). Safe to run on several instances. */
@Component
@ConditionalOnProperty(name = "ai-assistant.config.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledPublishJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublishJob.class);

    private final ConfigService configService;

    public ScheduledPublishJob(ConfigService configService) {
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
