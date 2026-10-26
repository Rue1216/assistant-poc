package com.example.copilot.aiassistant.evaluation;

import java.util.List;

/** The validated cases plus the SHA-256 (lowercase hex) of the raw file bytes. */
public record EvalDataset(List<EvalCase> cases, String hash) {

    public EvalDataset {
        cases = List.copyOf(cases);
    }
}
