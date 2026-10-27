package com.example.copilot.aiassistant.evaluation;

import com.example.copilot.aiassistant.port.AccessLevel;
import java.util.List;

/** One evaluation question. {@code customerId} and {@code accessLevel} only ever come from the dataset file. */
public record EvalCase(String id, String group, AccessLevel accessLevel, String customerId, String question,
                       Expect expect, List<String> rubric) {

    public EvalCase {
        rubric = List.copyOf(rubric);
    }

    /**
     * Machine-checkable expectations; missing lists are empty. {@code category} is an instruction {@code configKey}
     * or {@code "none"}; null means the routing is not checked.
     */
    public record Expect(List<String> tools, List<String> forbiddenTools, boolean refusal,
                         List<String> mustContain, List<String> mustNotContain, String category) {

        public Expect {
            tools = List.copyOf(tools);
            forbiddenTools = List.copyOf(forbiddenTools);
            mustContain = List.copyOf(mustContain);
            mustNotContain = List.copyOf(mustNotContain);
        }
    }
}
