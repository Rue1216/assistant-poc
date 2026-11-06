package com.example.copilot.aiassistant.application.feedback;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;
import com.example.copilot.aiassistant.domain.repository.AuditRepository;
import com.example.copilot.aiassistant.application.support.RequestContext;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Thumbs ratings stored on the audit document (spec 3.2). Statistics live in {@link FeedbackQueryService}. */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final AuditRepository audits;
    private final Clock clock;

    public FeedbackService(AuditRepository audits, Clock clock) {
        this.audits = audits;
        this.clock = clock;
    }

    /**
     * Sets or (null rating) clears the user's rating. Another user's answer, a failed answer and an unknown
     * request id are indistinguishable. When a request id repeats, the newest document is updated.
     */
    public void rate(String requestId, String userId, FeedbackRating rating) {
        if (!audits.setFeedback(requestId, userId, rating, clock.instant())) {
            throw new AssistantException(ErrorCode.AI_FEEDBACK_NOT_FOUND);
        }
        log.info("feedback set requestId={} answerRequestId={} userId={} rating={}",
                RequestContext.currentRequestId(), requestId, userId, rating == null ? "NONE" : rating);
    }
}
