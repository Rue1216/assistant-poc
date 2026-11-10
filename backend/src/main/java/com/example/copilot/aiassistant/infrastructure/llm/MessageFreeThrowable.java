package com.example.copilot.aiassistant.infrastructure.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Copy of a throwable that keeps exception types and stack frames but drops every message, because provider
 * messages may carry customer data.
 */
final class MessageFreeThrowable extends RuntimeException {

    private final String typeName;

    private MessageFreeThrowable(String typeName, StackTraceElement[] frames, Throwable cause) {
        super(null, cause, false, true);
        this.typeName = typeName;
        setStackTrace(frames);
    }

    /** Returns a message-free copy of the whole cause chain; stops at the first repeated cause. */
    static Throwable of(Throwable source) {
        if (source == null) {
            return null;
        }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = source; t != null && seen.add(t); t = t.getCause()) {
            chain.add(t);
        }
        Throwable copy = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            Throwable original = chain.get(i);
            copy = new MessageFreeThrowable(original.getClass().getName(), original.getStackTrace(), copy);
        }
        return copy;
    }

    @Override
    public String toString() {
        return typeName;
    }
}
