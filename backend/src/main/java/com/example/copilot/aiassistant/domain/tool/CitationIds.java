package com.example.copilot.aiassistant.domain.tool;

import java.util.ArrayList;
import java.util.List;

/** Hands out citation ids S1, S2... for one question. Not thread-safe: one instance per question. */
public final class CitationIds {

    private final List<String> issued = new ArrayList<>();

    /** The id the next {@link #next()} call will return. */
    String peek() {
        return "S" + (issued.size() + 1);
    }

    public String next() {
        String id = peek();
        issued.add(id);
        return id;
    }

    public List<String> issued() {
        return List.copyOf(issued);
    }
}
