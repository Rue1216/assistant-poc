package com.example.copilot.aiassistant.interfaces.rest;

import com.example.copilot.aiassistant.AiAssistantProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfiguration {

    @Bean
    PerUserRateLimiter chatRateLimiter(Clock clock, AiAssistantProperties props) {
        return new PerUserRateLimiter(clock, props.limits().rateLimitPerMinute());
    }
}
