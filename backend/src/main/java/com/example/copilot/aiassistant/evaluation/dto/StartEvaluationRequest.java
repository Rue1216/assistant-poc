package com.example.copilot.aiassistant.evaluation.dto;

import jakarta.validation.constraints.NotNull;

/** Starts an evaluation of the working copy; it carries no customer or access fields (those come from the dataset). */
public record StartEvaluationRequest(@NotNull Long docVersion) {
}
