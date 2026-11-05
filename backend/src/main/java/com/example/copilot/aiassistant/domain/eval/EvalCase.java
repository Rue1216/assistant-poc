package com.example.copilot.aiassistant.domain.eval;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import java.util.List;

/** One evaluation question. {@code certNo} and {@code accessLevel} only ever come from the dataset file. */
public record EvalCase(String id, String group, AccessLevel accessLevel, String certNo, String question,
                       Expect expect, List<String> rubric) {

    public EvalCase {
        rubric = List.copyOf(rubric);
    }

    /**
     * Machine-checkable expectations; missing lists are empty. {@code category} is an instruction {@code configKey}
     * or {@code "none"}; null means the routing is not checked. {@code toolOrder} must appear as a subsequence of
     * the calls; empty checks nothing.
     */
    public record Expect(List<String> tools, List<String> forbiddenTools, boolean refusal,
                         List<String> mustContain, List<String> mustNotContain, String category,
                         List<String> toolOrder) {

        public Expect {
            tools = List.copyOf(tools);
            forbiddenTools = List.copyOf(forbiddenTools);
            mustContain = List.copyOf(mustContain);
            mustNotContain = List.copyOf(mustNotContain);
            toolOrder = List.copyOf(toolOrder);
        }

        public Expect(List<String> tools, List<String> forbiddenTools, boolean refusal,
                      List<String> mustContain, List<String> mustNotContain, String category) {
            this(tools, forbiddenTools, refusal, mustContain, mustNotContain, category, List.of());
        }
    }
}
