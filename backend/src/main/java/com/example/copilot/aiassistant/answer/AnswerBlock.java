package com.example.copilot.aiassistant.answer;

import java.util.List;

/** One block of the answer; {@code citations} are citation numbers (1, 2...), not S ids. */
public record AnswerBlock(BlockType type, String text, List<Integer> citations) {

    public AnswerBlock {
        citations = List.copyOf(citations);
    }
}
