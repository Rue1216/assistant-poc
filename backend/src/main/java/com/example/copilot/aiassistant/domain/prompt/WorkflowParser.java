package com.example.copilot.aiassistant.domain.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the inline {@code {{tool: option, option}}} markers admins put in instruction text. Both half-width and
 * full-width colons and commas are accepted. Malformed markers are kept so the tool guard can reject them at run time.
 */
public final class WorkflowParser {

    public record Marker(int line, String toolName, List<String> options, boolean wellFormed) {}

    private static final Pattern MARKER = Pattern.compile("\\{\\{([^{}\\r\\n]*)\\}\\}");
    private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern OPTION_SEPARATOR = Pattern.compile("[,，]");

    private static final String QUERIED_SUFFIX = "（系統已查詢）";

    private WorkflowParser() {}

    /** Every marker in order; {@code line} is 1-based. */
    public static List<Marker> markers(String content) {
        List<Marker> result = new ArrayList<>();
        Matcher m = MARKER.matcher(content);
        while (m.find()) {
            result.add(parse(m.group(1), lineOf(content, m.start())));
        }
        return result;
    }

    /** Markers merged by tool name: position of the first, options a union in first-seen order. */
    public static List<WorkflowStep> steps(String content) {
        Map<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        for (Marker marker : markers(content)) {
            merged.computeIfAbsent(marker.toolName(), k -> new LinkedHashSet<>()).addAll(marker.options());
        }
        return merged.entrySet().stream()
                .map(e -> new WorkflowStep(e.getKey(), List.copyOf(e.getValue()))).toList();
    }

    /** Replaces each marker with its tool name, followed by a note when the system already ran the tools. */
    public static String render(String content, boolean queried) {
        return MARKER.matcher(content).replaceAll(m ->
                Matcher.quoteReplacement(parse(m.group(1), 0).toolName() + (queried ? QUERIED_SUFFIX : "")));
    }

    private static Marker parse(String body, int line) {
        int colon = indexOfColon(body);
        String name = (colon < 0 ? body : body.substring(0, colon)).strip();
        List<String> options = colon < 0 ? List.of()
                : OPTION_SEPARATOR.splitAsStream(body.substring(colon + 1)).map(String::strip).toList();
        boolean wellFormed = TOOL_NAME.matcher(name).matches() && options.stream().noneMatch(String::isEmpty);
        return new Marker(line, name, options, wellFormed);
    }

    private static int indexOfColon(String body) {
        int half = body.indexOf(':');
        int full = body.indexOf('：');
        if (half < 0) {
            return full;
        }
        return full < 0 ? half : Math.min(half, full);
    }

    private static int lineOf(String content, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
