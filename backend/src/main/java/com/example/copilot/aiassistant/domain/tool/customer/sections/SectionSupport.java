package com.example.copilot.aiassistant.domain.tool.customer.sections;

import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;

/** Helpers shared by the section formatters. Logs carry block and field names only, never values. */
@Slf4j
public final class SectionSupport {

    private static final int MAX_TEXT_CODE_POINTS = 200;

    private SectionSupport() {
    }

    /** Logs the name of a block the platform did not deliver; never any customer data. */
    public static void missing(String block) {
        log.warn("customer 360 block missing: {}", block);
    }

    /** Parses a platform number string; null (and a warn log without the value) when absent or malformed. */
    public static BigDecimal decimal(String text, String field) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            log.warn("customer 360 number malformed: {}", field);
            return null;
        }
    }

    /** Free text from the platform: redacted first, then cut, so a partially cut raw value is never left behind (a mask token may be cut, which is harmless). */
    public static String free(PiiRedactor redactor, String text, CustomerContext who) {
        if (text == null) {
            return null;
        }
        String redacted = redactor.redact(text, who);
        if (redacted.codePointCount(0, redacted.length()) <= MAX_TEXT_CODE_POINTS) {
            return redacted;
        }
        return redacted.substring(0, redacted.offsetByCodePoints(0, MAX_TEXT_CODE_POINTS));
    }
}
