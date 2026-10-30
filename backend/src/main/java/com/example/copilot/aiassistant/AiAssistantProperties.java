package com.example.copilot.aiassistant;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-assistant")
public record AiAssistantProperties(Llm llm, Limits limits, Config config, Market market,
                                    Audit audit, Eval eval) {

    /**
     * {@code temperature}, {@code reasoningEffort}: null (blank in yaml) leaves the field out of the request, for
     * models that only accept the default.
     */
    public record Llm(String provider, String apiKey, String modelName, Double temperature,
                      String reasoningEffort, Duration timeout, int maxOutputTokens) {

        /** The API key must never reach a log line or an error message. */
        @Override
        public String toString() {
            return "Llm[provider=" + provider + ", apiKey=***, modelName=" + modelName
                    + ", temperature=" + temperature + ", reasoningEffort=" + reasoningEffort + ", timeout=" + timeout
                    + ", maxOutputTokens=" + maxOutputTokens + "]";
        }
    }

    public record Limits(int messageMaxChars, int historyMaxTurns, int historyMaxChars,
                         int maxToolCalls, int maxModelRounds, Duration requestTimeout,
                         int tokenBudget, Duration toolTimeout, int toolResultMaxBytes,
                         int rateLimitPerMinute, int summaryMaxChars) {
    }

    /** {@code allowedUrlHosts}: hosts a prompt may link to; empty means no URL is allowed. */
    public record Config(Duration scheduleCheckInterval, Duration scheduleMinLead,
                         Duration scheduleMaxLead, List<String> allowedUrlHosts) {
    }

    public record Market(Twse twse) {
    }

    /** {@code storeToolResults}: keep each successful tool result in the audit document (default off). */
    public record Audit(boolean storeToolResults) {
    }

    /**
     * Prompt evaluation. {@code dataset}: resource location of the case file; {@code runTimeout}: hard stop for one
     * run; {@code staleAfter}: a RUNNING record without progress for this long is treated as dead.
     */
    public record Eval(String dataset, Duration runTimeout, Duration staleAfter) {
    }

    public record Twse(String baseUrl, List<String> allowedHosts, Duration connectTimeout,
                       Duration readTimeout, int maxResponseBytes, Duration cacheTtl) {
    }
}
