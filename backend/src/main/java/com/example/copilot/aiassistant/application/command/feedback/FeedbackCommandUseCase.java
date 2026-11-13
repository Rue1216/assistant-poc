package com.example.copilot.aiassistant.application.command.feedback;

import com.example.copilot.aiassistant.domain.audit.TraceId;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;
import com.example.copilot.aiassistant.domain.repository.AuditRepository;
import com.example.copilot.aiassistant.application.support.RequestContext;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import com.example.shared.annotation.UseCase;

/** Thumbs ratings stored on the audit document (spec 3.2). Statistics live in {@link com.example.copilot.aiassistant.application.query.feedback.FeedbackQueryService}. */
@UseCase
@Slf4j
public class FeedbackCommandUseCase {

    private final AuditRepository audits;
    private final Clock clock;

    public FeedbackCommandUseCase(AuditRepository audits, Clock clock) {
        this.audits = audits;
        this.clock = clock;
    }

    /**
     * Sets or (null rating) clears the user's rating. Another user's answer, a failed answer and an unknown
     * trace id are indistinguishable. When a trace id repeats, the newest document is updated.
     */
    public void rate(TraceId traceId, String userId, FeedbackRating rating) {
        if (!audits.setFeedback(traceId, userId, rating, clock.instant())) {
            throw new AssistantException(ErrorCode.AI_FEEDBACK_NOT_FOUND);
        }
        log.info("feedback set traceId={} answerTraceId={} userId={} rating={}",
                RequestContext.currentTraceId(), traceId, userId, rating == null ? "NONE" : rating);
    }
}
