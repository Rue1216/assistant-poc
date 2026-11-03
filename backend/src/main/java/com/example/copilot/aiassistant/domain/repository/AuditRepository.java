package com.example.copilot.aiassistant.domain.repository;

import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;
import java.time.Instant;

/** Stores the audit record of each question and the thumbs rating later attached to it. */
public interface AuditRepository {

    void insert(AuditRecord record);

    /**
     * Sets, or clears when {@code rating} is null, the rating of the user's ANSWERED record with this request id
     * (the newest one when the id repeats), stamping {@code at}. Returns false when no record matched.
     */
    boolean setFeedback(String requestId, String userId, FeedbackRating rating, Instant at);
}
