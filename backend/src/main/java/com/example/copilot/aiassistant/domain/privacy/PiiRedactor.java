package com.example.copilot.aiassistant.domain.privacy;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Free-text de-identification, rules 1 to 7 of 03-tool-catalog section 1.6. */
public class PiiRedactor {

    public static final String SELF = "【本顧客】";
    static final String MASKED = "【已遮蔽】";
    static final String OTHER = "【其他顧客】";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    /** ASCII punctuation that identifiers, numbers, emails and phone numbers may contain; NFKC folds to these are kept. */
    private static final String FOLDABLE_PUNCTUATION = "-@._%+ ()";
    /** A run of separators that may sit between the digits of a phone or account number. */
    private static final String SEP = "[\\s\\p{Pd}\\u2212.()]*";
    private static final Pattern NATIONAL_ID = Pattern.compile("[A-Z][12]\\d{8}(?!\\d)", FLAGS);
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)09\\d{2}-?\\d{3}-?\\d{3}(?!\\d)", FLAGS);
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", FLAGS);
    private static final Pattern ACCOUNT = Pattern.compile("(?<!\\d)\\d{10,}(?!\\d)", FLAGS);
    /** Cert number formats (national id, resident certificate, foreign national); shared with the config validator. */
    public static final String CERT_NO_REGEX =
            "(?<![A-Za-z0-9&])(?:[A-Z][12]\\d{8}|[A-Z]{3}\\d{6}|&[A-Z]\\d{8})(?![A-Za-z0-9])";
    private static final Pattern OTHER_CUSTOMER = Pattern.compile(CERT_NO_REGEX, FLAGS);

    public String redact(String text, CustomerContext customer) {
        Objects.requireNonNull(customer, "customer");
        if (text == null) {
            return null;
        }
        String result = foldWidth(text);
        // 1: the customer's own name and id
        for (String ref : longestFirst(customer.selfReferences())) {
            result = replaceKnown(result, ref, SELF);
        }
        // 2: the customer's own identifying values
        for (String value : longestFirst(customer.sensitiveValues())) {
            result = replaceKnown(result, value, MASKED);
        }
        // 3: other customers' cert numbers; before the national id rule, which would mask the same values
        result = replaceAll(OTHER_CUSTOMER, result, OTHER);
        // 4-7: format based
        result = replaceAll(NATIONAL_ID, result, MASKED);
        result = replaceAll(MOBILE, result, MASKED);
        result = replaceAll(EMAIL, result, MASKED);
        return replaceAll(ACCOUNT, result, MASKED);
    }

    /**
     * Per-character NFKC (full-width, compatibility radicals, circled digits ...) after dropping invisible format
     * characters (zero-width space, soft hyphen ...). A fold to ASCII punctuation such as '?' is not adopted, so
     * ordinary full-width sentence punctuation stays as typed; folds to identifier punctuation are adopted.
     */
    private static String foldWidth(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (Character.getType(cp) == Character.FORMAT) {
                return;
            }
            String original = new String(Character.toChars(cp));
            String folded = Normalizer.normalize(original, Normalizer.Form.NFKC);
            boolean asciiPunctuation = folded.length() == 1 && folded.charAt(0) < 128
                    && !Character.isLetterOrDigit(folded.charAt(0))
                    && FOLDABLE_PUNCTUATION.indexOf(folded.charAt(0)) < 0;
            sb.append(asciiPunctuation ? original : folded);
        });
        return sb.toString();
    }

    private static String replaceAll(Pattern pattern, String text, String token) {
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(token));
    }

    /** Longest first so a value is never partly replaced by a shorter one it contains. */
    private static List<String> longestFirst(Set<String> values) {
        return values.stream()
                .map(PiiRedactor::foldWidth)
                .filter(v -> !v.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private static String replaceKnown(String text, String known, String token) {
        boolean numeric = known.chars().allMatch(c -> Character.isDigit(c) || c == '-' || c == ' ');
        if (numeric) {
            String digits = known.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                return text; // a degenerate value must not turn into a pattern that matches everywhere
            }
            return replaceAll(digitPattern(digits), text, token);
        }
        return replaceAll(spacedLiteral(known), text, token);
    }

    /** Digit-only values (phone, account) match with any run of separators between digits; phones also as +886. */
    private static Pattern digitPattern(String digits) {
        String local = separated(digits);
        if (digits.startsWith("09") && digits.length() > 1) {
            local = "(?:" + local + "|\\+?886" + SEP + separated(digits.substring(1)) + ")";
        }
        return Pattern.compile("(?<!\\d)" + local + "(?!\\d)");
    }

    private static String separated(String digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) {
                sb.append(SEP);
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /** Names, ids and emails match literally but tolerate whitespace between characters. */
    private static Pattern spacedLiteral(String known) {
        String[] chars = known.codePoints().filter(cp -> !Character.isWhitespace(cp))
                .mapToObj(cp -> Pattern.quote(new String(Character.toChars(cp)))).toArray(String[]::new);
        return Pattern.compile(String.join("\\s*", chars), FLAGS);
    }
}
