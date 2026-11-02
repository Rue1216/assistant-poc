package com.example.copilot.aiassistant.domain.eval;

/** Error codes that are only written into evaluation records; they are not API error codes. */
public final class EvalErrors {

    public static final String TIMEOUT = "AI_EVAL_TIMEOUT";
    public static final String INTERRUPTED = "AI_EVAL_INTERRUPTED";
    public static final String JUDGE_FAILED = "AI_EVAL_JUDGE_FAILED";
    /** The model could not be reached or refused for load: every later case would fail the same way. */
    public static final String LLM_UNAVAILABLE = "AI_EVAL_LLM_UNAVAILABLE";

    private EvalErrors() {
    }
}
