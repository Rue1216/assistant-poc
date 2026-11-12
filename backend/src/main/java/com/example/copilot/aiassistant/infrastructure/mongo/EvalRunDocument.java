package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.eval.EvalStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.Setter;

/**
 * Stored form of {@code EvalRun}; no {@code _class} is written and reference fields are kept as explicit nulls.
 * Numeric fields are primitives so runs written before a field existed read it as 0. Lists default to empty so a
 * read that leaves {@code cases} out (or an old run) yields an empty list.
 */
@Getter
@Setter
@Document(AiAssistantCollections.EVAL_RUN)
public class EvalRunDocument {

    /** 主鍵 */
    @Id
    private String id;
    /** Config key under evaluation */
    @Field(write = Field.Write.ALWAYS)
    private String configKey;
    /** Candidate version */
    private int candidateVersion;
    /** Candidate document version at start */
    private long candidateDocVersion;
    /** Candidate last update time at start */
    @Field(write = Field.Write.ALWAYS)
    private Instant candidateUpdatedAt;
    /** Published versions used as baseline */
    @Field(write = Field.Write.ALWAYS)
    private List<PromptVersionDocument> baselineVersions = new ArrayList<>();
    /** Hash of the dataset */
    @Field(write = Field.Write.ALWAYS)
    private String datasetHash;
    /** Run status */
    @Field(write = Field.Write.ALWAYS)
    private EvalStatus status;
    /** Error code when the run failed */
    @Field(write = Field.Write.ALWAYS)
    private String errorCode;
    /** Who started the run */
    @Field(write = Field.Write.ALWAYS)
    private String triggeredBy;
    /** Start time */
    @Field(write = Field.Write.ALWAYS)
    private Instant startedAt;
    /** Last heartbeat */
    @Field(write = Field.Write.ALWAYS)
    private Instant heartbeatAt;
    /** Finish time; null while running */
    @Field(write = Field.Write.ALWAYS)
    private Instant finishedAt;
    /** Number of cases in the dataset */
    private int totalCases;
    /** Number of finished cases */
    private int completedCases;
    /** Run-level summary; null until completed */
    @Field(write = Field.Write.ALWAYS)
    private SummaryDocument summary;
    /** Case results */
    @Field(write = Field.Write.ALWAYS)
    private List<CaseDocument> cases = new ArrayList<>();
    /** Guard for the conditional updates; a plain field, not a version annotation */
    private long docVersion;

    /** Run-level metrics of both sides. */
    @Getter
    @Setter
    public static class SummaryDocument {

        /** Baseline metrics */
        @Field(write = Field.Write.ALWAYS)
        private MetricsDocument baseline;
        /** Candidate metrics */
        @Field(write = Field.Write.ALWAYS)
        private MetricsDocument candidate;
        /** Tokens the judge used */
        private long judgeTokens;
    }

    /** Aggregated numbers of one side. */
    @Getter
    @Setter
    public static class MetricsDocument {

        /** Passed cases */
        private int correct;
        /** Evaluated cases */
        private int total;
        /** Cases with the expected tools */
        private int toolsOk;
        /** Expected refusals that were refused */
        private int refusedExpected;
        /** Cases expecting a refusal */
        private int expectedRefusals;
        /** Normal cases that were refused */
        private int refusedNormal;
        /** Cases not expecting a refusal */
        private int normalCases;
        /** Correctly routed cases; absent in old runs */
        private int routeOk;
        /** Cases with an expected category; absent in old runs */
        private int routeCases;
        /** Average total tokens */
        private long avgTotalTokens;
        /** Average input tokens */
        private long avgInputTokens;
        /** Average output tokens */
        private long avgOutputTokens;
        /** Average duration (ms) */
        private long avgDurationMs;
    }

    /** Both sides of one evaluated case. */
    @Getter
    @Setter
    public static class CaseDocument {

        /** Case id */
        @Field(write = Field.Write.ALWAYS)
        private String caseId;
        /** Case group */
        @Field(write = Field.Write.ALWAYS)
        private String group;
        /** Question */
        @Field(write = Field.Write.ALWAYS)
        private String question;
        /** Whether a refusal is expected */
        private boolean expectRefusal;
        /** Expected route category */
        @Field(write = Field.Write.ALWAYS)
        private String expectCategory;
        /** Baseline result */
        @Field(write = Field.Write.ALWAYS)
        private ResultDocument baseline;
        /** Candidate result */
        @Field(write = Field.Write.ALWAYS)
        private ResultDocument candidate;
    }

    /** One side of one evaluated case. */
    @Getter
    @Setter
    public static class ResultDocument {

        /** Outcome of the turn */
        @Field(write = Field.Write.ALWAYS)
        private String outcome;
        /** Error code when the turn failed */
        @Field(write = Field.Write.ALWAYS)
        private String errorCode;
        /** Shown answer */
        @Field(write = Field.Write.ALWAYS)
        private String answer;
        /** Requested tool calls */
        @Field(write = Field.Write.ALWAYS)
        private List<ToolCallDocument> toolCalls = new ArrayList<>();
        /** Model calls */
        private int modelCalls;
        /** Input tokens */
        private int inputTokens;
        /** Output tokens */
        private int outputTokens;
        /** Total tokens */
        private int totalTokens;
        /** Duration (ms) */
        private int durationMs;
        /** Numbers no tool result backs */
        @Field(write = Field.Write.ALWAYS)
        private List<String> unverifiedNumbers = new ArrayList<>();
        /** Whether the expected tools ran */
        private boolean toolsOk;
        /** Whether all numbers are verified */
        private boolean numbersOk;
        /** Expected phrases missing from the answer */
        @Field(write = Field.Write.ALWAYS)
        private List<String> missingPhrases = new ArrayList<>();
        /** Forbidden phrases found in the answer */
        @Field(write = Field.Write.ALWAYS)
        private List<String> forbiddenPhrases = new ArrayList<>();
        /** Judge verdict; null when not judged */
        @Field(write = Field.Write.ALWAYS)
        private JudgeDocument judge;
        /** Whether the case passed */
        private boolean passed;
        /** Whether the route was right; null without an expectation */
        @Field(write = Field.Write.ALWAYS)
        private Boolean routeOk;
        /** How the instruction was chosen */
        @Field(write = Field.Write.ALWAYS)
        private String routeSource;
        /** Chosen instruction key */
        @Field(write = Field.Write.ALWAYS)
        private String instructionKey;
    }

    /** One requested tool call. */
    @Getter
    @Setter
    public static class ToolCallDocument {

        /** Tool name */
        @Field(write = Field.Write.ALWAYS)
        private String toolName;
        /** Tool status */
        @Field(write = Field.Write.ALWAYS)
        private String status;
    }

    /** The judge model verdict. */
    @Getter
    @Setter
    public static class JudgeDocument {

        /** Whether the answer was a refusal; null when judging failed */
        @Field(write = Field.Write.ALWAYS)
        private Boolean refused;
        /** Rubric results */
        @Field(write = Field.Write.ALWAYS)
        private List<RubricDocument> rubric = new ArrayList<>();
        /** Judge comment */
        @Field(write = Field.Write.ALWAYS)
        private String comment;
        /** Error when judging failed */
        @Field(write = Field.Write.ALWAYS)
        private String error;
        /** Judge tokens */
        private int tokens;
    }

    /** One rubric item result. */
    @Getter
    @Setter
    public static class RubricDocument {

        /** Rubric text */
        @Field(write = Field.Write.ALWAYS)
        private String text;
        /** Whether it was met */
        private boolean met;
    }
}
