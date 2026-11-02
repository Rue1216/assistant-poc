package com.example.copilot.aiassistant.domain.eval;

import java.util.List;

/**
 * Result of the machine-checkable expectations of one case (prompt-evaluation spec 4.4). {@code routeOk} is null
 * when the case has no expected category.
 */
public record RuleChecks(boolean toolsOk, boolean numbersOk, List<String> missingPhrases,
                         List<String> forbiddenPhrases, Boolean routeOk) {
}
