package com.example.copilot.aiassistant.domain.tool.market;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;

/** Converts the string formats TWSE uses. Anything unparseable becomes null. */
public final class TwseParsing {

    private static final int ROC_EPOCH_YEAR = 1911;

    private TwseParsing() {
    }

    /** ROC date such as {@code 1151016} to {@code 2026-10-16}. */
    public static LocalDate rocDate(String yyyMMdd) {
        if (yyyMMdd == null) {
            return null;
        }
        String s = yyyMMdd.trim();
        if (!s.matches("\\d{6,7}")) {
            return null;
        }
        try {
            int split = s.length() - 4;
            return LocalDate.of(Integer.parseInt(s.substring(0, split)) + ROC_EPOCH_YEAR,
                    Integer.parseInt(s.substring(split, split + 2)), Integer.parseInt(s.substring(split + 2)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Number with optional thousands separators; {@code --} and blanks give null. */
    public static BigDecimal number(String s) {
        if (s == null) {
            return null;
        }
        String cleaned = s.trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code 70003} to {@code 07:00:03}. */
    public static String time(String hmmss) {
        if (hmmss == null) {
            return null;
        }
        String s = hmmss.trim();
        if (!s.matches("\\d{5,6}")) {
            return null;
        }
        s = "0".repeat(6 - s.length()) + s;
        int hour = Integer.parseInt(s.substring(0, 2));
        int minute = Integer.parseInt(s.substring(2, 4));
        int second = Integer.parseInt(s.substring(4));
        if (hour >= 24 || minute >= 60 || second >= 60) {
            return null;
        }
        return s.substring(0, 2) + ":" + s.substring(2, 4) + ":" + s.substring(4);
    }
}
