package com.example.copilot.aiassistant.domain.prompt;

import java.util.ArrayList;
import java.util.List;

/** Extracts the "- " bullets under the "## 範例問題" heading. */
public class ExampleQuestionParser {

    private static final String HEADING = "## 範例問題";

    public List<String> parse(String markdown) {
        List<String> questions = new ArrayList<>();
        if (markdown == null) {
            return questions;
        }
        boolean inSection = false;
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                inSection = line.trim().equals(HEADING);
            } else if (inSection && line.startsWith("- ")) {
                String text = line.substring(2).trim();
                if (!text.isEmpty()) {
                    questions.add(text);
                }
            }
        }
        return questions;
    }
}
