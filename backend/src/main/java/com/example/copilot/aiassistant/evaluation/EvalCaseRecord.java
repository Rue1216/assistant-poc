package com.example.copilot.aiassistant.evaluation;

/** Both sides of one evaluated case, as stored in the run document. */
public record EvalCaseRecord(String caseId, String group, String question, boolean expectRefusal,
                             String expectCategory, CaseResult baseline, CaseResult candidate) {
}
