package com.example.copilot.aiassistant.domain.prompt;

/**
 * The routing outcome. {@code instructionKey} is null for "none" and for {@link RouteSource#FALLBACK}; the token
 * counts are those of the routing model call (0 when it was not made or failed).
 */
public record RouteDecision(String instructionKey, RouteSource source, boolean modelCalled, long inputTokens,
                            long outputTokens, long totalTokens) {

    /** The selection to assemble: a fallback loads every instruction, a null key loads none. */
    public InstructionSelection selection() {
        if (source == RouteSource.FALLBACK) {
            return InstructionSelection.all();
        }
        return instructionKey == null ? InstructionSelection.none() : InstructionSelection.only(instructionKey);
    }
}
