package com.example.copilot.aiassistant;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-assistant")
public record AiAssistantProperties(Llm llm, Limits limits, Config config, Market market) {

    public record Llm(String provider, String apiKey, String modelName, double temperature,
                      Duration timeout, int maxOutputTokens) {
    }

    public record Limits(int messageMaxChars, int historyMaxTurns, int historyMaxChars,
                         int maxToolCalls, int maxModelRounds, Duration requestTimeout,
                         int tokenBudget, Duration toolTimeout, int toolResultMaxBytes,
                         int rateLimitPerMinute) {
    }

    public record Config(Duration scheduleCheckInterval, Duration scheduleMinLead,
                         Duration scheduleMaxLead) {
    }

    public record Market(Twse twse) {
    }

    public record Twse(String baseUrl, List<String> allowedHosts, Duration connectTimeout,
                       Duration readTimeout, int maxResponseBytes, Duration cacheTtl) {
    }
}
