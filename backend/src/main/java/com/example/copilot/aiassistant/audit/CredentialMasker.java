package com.example.copilot.aiassistant.audit;

import com.example.copilot.aiassistant.config.ConfigValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Masks suspected credentials (spec 04 section 2.5 "API key / token" and "密碼" patterns). */
@Component
public class CredentialMasker {

    static final String REDACTED = "[REDACTED]";

    private static final String PASSWORD_WITH_VALUE_REGEX =
            ConfigValidator.PASSWORD_REGEX + "\\s*[^\\s，。、；：！？「」『』（）【】《》〈〉…—]+";

    private static final Pattern PATTERN;

    static {
        List<String> regexes = new ArrayList<>(ConfigValidator.API_KEY_REGEXES);
        // Masking also removes the value after the label (detection in ConfigValidator only needs the label).
        regexes.add(PASSWORD_WITH_VALUE_REGEX);
        PATTERN = Pattern.compile(String.join("|", regexes), Pattern.CASE_INSENSITIVE);
    }

    public String mask(String text) {
        return text == null ? null : PATTERN.matcher(text).replaceAll(REDACTED);
    }
}
