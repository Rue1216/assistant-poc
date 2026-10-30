package com.example.copilot.aiassistant.prompt;

/**
 * Outcome of one history summarization. {@code text} is null when {@code status} is {@link SummaryStatus#FAILED};
 * {@code summarizedCount} is the number of overflow messages handed to the summarizer.
 */
public record SummaryResult(SummaryStatus status, String text, int summarizedCount, long inputTokens,
                            long outputTokens, long totalTokens) {
}
