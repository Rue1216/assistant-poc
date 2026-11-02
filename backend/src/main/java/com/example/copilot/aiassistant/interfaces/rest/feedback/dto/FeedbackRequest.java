package com.example.copilot.aiassistant.interfaces.rest.feedback.dto;

import com.example.copilot.aiassistant.domain.feedback.FeedbackRating;


/** Body of the rating call; a null {@code rating} cancels an earlier rating. */
public record FeedbackRequest(FeedbackRating rating) {
}
