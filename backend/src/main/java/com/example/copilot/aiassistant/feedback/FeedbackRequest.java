package com.example.copilot.aiassistant.feedback;

/** Body of the rating call; a null {@code rating} cancels an earlier rating. */
public record FeedbackRequest(FeedbackRating rating) {
}
