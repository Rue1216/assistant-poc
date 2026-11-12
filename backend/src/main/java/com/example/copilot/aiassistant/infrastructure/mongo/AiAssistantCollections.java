package com.example.copilot.aiassistant.infrastructure.mongo;

/** MongoDB collections owned by the AI assistant. */
public final class AiAssistantCollections {
    /** Assistant config (prompts and instructions) collection name. */
    public static final String CONFIG = "COLL_ICRM_AI_ASSISTANT_CONFIG";
    /** Chat audit record collection name. */
    public static final String AUDIT = "COLL_ICRM_AI_ASSISTANT_AUDIT";
    /** Prompt evaluation run collection name. */
    public static final String EVAL_RUN = "COLL_ICRM_AI_ASSISTANT_EVAL_RUN";

    private AiAssistantCollections() {
        // private constructor to prevent instantiation
    }
}
