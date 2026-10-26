package com.example.copilot.aiassistant.evaluation.dto;

import com.example.copilot.aiassistant.evaluation.EvalCase;
import java.util.List;

/** The evaluation dataset as shown to admins (prompt-evaluation spec 6.6). */
public record EvalCasesView(String datasetHash, List<EvalCase> cases) {
}
