package com.example.copilot.aiassistant.answer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The model's answer after validation. {@code citationNumbers} maps S id to citation number in number order
 * (cited ones first, then the uncited successful calls); {@code cited} holds the S ids some block cites.
 * {@code annotatedAnswer} is for the audit record only.
 */
public record AssembledAnswer(List<AnswerBlock> blocks, String answer, String annotatedAnswer,
                              Map<String, Integer> citationNumbers, Set<String> cited, boolean truncated,
                              List<String> unverifiedNumbers) {
}
