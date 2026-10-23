package com.example.copilot.aiassistant.answer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Finds numbers in the answer that appear nowhere in this question's tool data
 * (answer-citations spec 2.4). The result goes to the audit record only.
 */
public final class NumberChecker {

    private static final int MAX_RESULTS = 20;

    // Alternatives in order: full date, month/day, number (thousands separator or plain decimal).
    private static final Pattern TOKEN = Pattern.compile(
            "(?<fullDate>\\d{4}[-/](?<fy>\\d{1,2})[-/](?<fd>\\d{1,2}))"
                    + "|(?<![\\d/])(?<mm>\\d{1,2})/(?<md>\\d{1,2})(?![\\d/])"
                    + "|(?<num>\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)");

    private NumberChecker() {
    }

    /** Numbers of the answer texts that no tool data contains, as written, first-seen order, at most 20. */
    public static List<String> unverified(List<String> texts, List<JsonNode> toolData) {
        Set<String> known = new HashSet<>();
        for (JsonNode node : toolData) {
            collect(node, known);
        }
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String text : texts) {
            Matcher m = TOKEN.matcher(text);
            while (m.find() && result.size() < MAX_RESULTS) {
                String key = key(m);
                if (key != null && !known.contains(key) && seen.add(key)) {
                    result.add(m.group());
                }
            }
        }
        return result;
    }

    private static void collect(JsonNode node, Set<String> known) {
        if (node.isNumber()) {
            known.add(normalize(node.decimalValue().abs()));
        } else if (node.isString()) {
            Matcher m = TOKEN.matcher(node.asString());
            while (m.find()) {
                String key = key(m);
                if (key != null) {
                    known.add(key);
                }
                if (m.group("fullDate") != null) {
                    // A date field also vouches for its year, month and day as plain numbers.
                    known.add(normalize(new BigDecimal(m.group("fullDate").substring(0, 4))));
                    known.add(normalize(new BigDecimal(m.group("fy"))));
                    known.add(normalize(new BigDecimal(m.group("fd"))));
                }
            }
        } else {
            node.forEach(child -> collect(child, known));
        }
    }

    /** Normalized comparison key of a match, or null when the number is skipped (integer 1 to 10). */
    private static String key(Matcher m) {
        if (m.group("fullDate") != null) {
            return dateKey(m.group("fy"), m.group("fd"));
        }
        if (m.group("mm") != null) {
            return dateKey(m.group("mm"), m.group("md"));
        }
        String normalized = normalize(new BigDecimal(m.group("num").replace(",", "")));
        return isSmallInteger(normalized) ? null : normalized;
    }

    private static String dateKey(String month, String day) {
        return "D:" + Integer.parseInt(month) + "-" + Integer.parseInt(day);
    }

    private static String normalize(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static boolean isSmallInteger(String normalized) {
        return normalized.length() <= 2 && !normalized.contains(".")
                && Integer.parseInt(normalized) >= 1 && Integer.parseInt(normalized) <= 10;
    }
}
