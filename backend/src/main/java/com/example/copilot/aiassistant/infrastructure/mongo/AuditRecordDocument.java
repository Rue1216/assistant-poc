package com.example.copilot.aiassistant.infrastructure.mongo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.Setter;

/**
 * Stored form of {@code AuditRecord}; no {@code _class} is written and reference fields are kept as explicit nulls.
 */
@Getter
@Setter
@Document(AiAssistantCollections.AUDIT)
public class AuditRecordDocument {

    /** 主鍵 */
    @Id
    private String id;
    /** Trace id of the request */
    @Field(write = Field.Write.ALWAYS)
    private String traceId;
    /** Asking user */
    @Field(write = Field.Write.ALWAYS)
    private String userId;
    /** Role key of the user */
    @Field(write = Field.Write.ALWAYS)
    private String roleKey;
    /** Access level of the user */
    @Field(write = Field.Write.ALWAYS)
    private String accessLevel;
    /** Redacted question */
    @Field(write = Field.Write.ALWAYS)
    private String question;
    /** Shown answer; null when the turn failed */
    @Field(write = Field.Write.ALWAYS)
    private String answer;
    /** Whether the stored answer was cut */
    private boolean answerTruncated;
    /** Outcome of the turn */
    @Field(write = Field.Write.ALWAYS)
    private String outcome;
    /** Error code when the turn failed */
    @Field(write = Field.Write.ALWAYS)
    private String errorCode;
    /** Tool calls of the turn */
    @Field(write = Field.Write.ALWAYS)
    private List<ToolCallDocument> toolCalls;
    /** Turn duration (ms) */
    private int durationMs;
    /** Creation time */
    @Field(write = Field.Write.ALWAYS)
    private Instant createdAt;
    /** Numbers in the answer that no tool result backs */
    @Field(write = Field.Write.ALWAYS)
    private List<String> unverifiedNumbers;
    /** Number of model calls */
    private int modelCalls;
    /** Input tokens */
    private int inputTokens;
    /** Output tokens */
    private int outputTokens;
    /** Total tokens */
    private int totalTokens;
    /** Config versions that went into the prompt */
    @Field(write = Field.Write.ALWAYS)
    private List<PromptVersionDocument> promptVersions;
    /** How the instruction was chosen */
    @Field(write = Field.Write.ALWAYS)
    private String routeSource;
    /** Chosen instruction key */
    @Field(write = Field.Write.ALWAYS)
    private String instructionKey;
    /** Whether a history summary went into the prompt */
    private boolean summaryIn;
    /** Summary status */
    @Field(write = Field.Write.ALWAYS)
    private String summaryStatus;
    /** Number of summarized messages */
    private int summarizedMessages;
    /** Summary tokens */
    private int summaryTokens;
    /** Model name */
    @Field(write = Field.Write.ALWAYS)
    private String modelName;
    /** Filled in later by the feedback API; explicit null keeps the field present */
    @Field(write = Field.Write.ALWAYS)
    private String feedback;
    /** Time of the feedback */
    @Field(write = Field.Write.ALWAYS)
    private Instant feedbackAt;

    /** One tool call of the turn. */
    @Getter
    @Setter
    public static class ToolCallDocument {

        /** Tool name */
        @Field(write = Field.Write.ALWAYS)
        private String toolName;
        /** Tool status */
        @Field(write = Field.Write.ALWAYS)
        private String status;
        /** Error code of the call */
        @Field(write = Field.Write.ALWAYS)
        private String errorCode;
        /** Call duration (ms) */
        private int durationMs;
        /** Citation id */
        @Field(write = Field.Write.ALWAYS)
        private Integer citationId;
        /** Target of the call */
        @Field(write = Field.Write.ALWAYS)
        private String target;
        /** Data time of the result */
        @Field(write = Field.Write.ALWAYS)
        private String dataTime;
        /** Number of result rows */
        @Field(write = Field.Write.ALWAYS)
        private Integer resultCount;
        /** Hash of the result */
        @Field(write = Field.Write.ALWAYS)
        private String resultHash;
        /** Tool result; only stored when result storage is on, so not written when null */
        private Map<String, Object> result;
    }
}
