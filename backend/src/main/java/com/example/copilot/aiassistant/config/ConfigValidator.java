package com.example.copilot.aiassistant.config;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.prompt.ExampleQuestionParser;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Pre-publish validation of prompt configs (spec 04 section 2.5). */
@Component
public class ConfigValidator {

    static final int MAX_BYTES = 65_536;
    static final int MAX_EXAMPLES = 5;
    static final int MAX_EXAMPLE_LENGTH = 40;
    private static final String SCOPE_HEADING = "## 適用問題";

    /** Spec 04 section 2.5 credential patterns; shared with the audit CredentialMasker. */
    public static final List<String> API_KEY_REGEXES =
            List.of("sk-[A-Za-z0-9_-]{16,}", "Bearer\\s+[A-Za-z0-9._-]{16,}");
    public static final String PASSWORD_REGEX = "(password|passwd|密碼)\\s*[:：=]";

    private static final Pattern URL_HOST = Pattern.compile("(?i)https?://([^\\s/:?#]+)");

    private record Forbidden(String label, Predicate<String> matches) {
        static Forbidden regex(String label, String... regexes) {
            Pattern pattern = Pattern.compile(String.join("|", regexes), Pattern.CASE_INSENSITIVE);
            return new Forbidden(label, line -> pattern.matcher(line).find());
        }
    }

    private final ExampleQuestionParser exampleQuestionParser;
    private final Set<String> allowedUrlHosts;
    private final List<Forbidden> forbidden;

    @Autowired
    public ConfigValidator(ExampleQuestionParser exampleQuestionParser, AiAssistantProperties properties) {
        this(exampleQuestionParser, properties.config().allowedUrlHosts());
    }

    /** A null or empty host list allows no URL at all. */
    ConfigValidator(ExampleQuestionParser exampleQuestionParser, Collection<String> allowedUrlHosts) {
        this.exampleQuestionParser = exampleQuestionParser;
        this.allowedUrlHosts = allowedUrlHosts == null ? Set.of()
                : allowedUrlHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        this.forbidden = List.of(
                Forbidden.regex("API key 或 token", API_KEY_REGEXES.toArray(String[]::new)),
                Forbidden.regex("密碼", PASSWORD_REGEX),
                new Forbidden("未允許的網址", this::hasDisallowedUrl),
                Forbidden.regex("IP 位址", "\\b\\d{1,3}(\\.\\d{1,3}){3}\\b"),
                Forbidden.regex("身分證號", "\\b[A-Z][12]\\d{8}\\b"),
                Forbidden.regex("手機號碼", "\\b09\\d{2}-?\\d{3}-?\\d{3}\\b"),
                Forbidden.regex("顧客編號", PiiRedactor.CERT_NO_REGEX));
    }

    private boolean hasDisallowedUrl(String line) {
        Matcher m = URL_HOST.matcher(line);
        while (m.find()) {
            if (!allowedUrlHosts.contains(m.group(1).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Validates the content and also keeps forbidden content (spec 04 section 2.5) out of the display name. */
    public List<ValidationError> validate(ConfigType type, String name, String content) {
        List<ValidationError> errors = validate(type, content);
        if (name != null) {
            checkForbidden("名稱", new String[] {name}, errors, false);
        }
        return errors;
    }

    public List<ValidationError> validate(ConfigType type, String content) {
        List<ValidationError> errors = new ArrayList<>();
        if (content == null || content.isBlank()) {
            errors.add(new ValidationError("NOT_EMPTY", "內容不可為空"));
            return errors;
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            errors.add(new ValidationError("MAX_SIZE", "內容超過 64KB 上限"));
        }
        String[] lines = content.split("\\R", -1);
        if (type == ConfigType.SYSTEM_INSTRUCTION) {
            checkScope(lines, errors);
            checkExamples(content, errors);
        }
        checkForbidden(lines, errors);
        return errors;
    }

    /** The "## 適用問題" section must exist and have a non-blank body, as InstructionRouter reads it. */
    private static void checkScope(String[] lines, List<ValidationError> errors) {
        boolean inScope = false;
        boolean found = false;
        boolean hasBody = false;
        for (String line : lines) {
            if (inScope && line.startsWith("## ")) {
                break;
            }
            if (inScope) {
                hasBody |= !line.isBlank();
            } else if (line.stripTrailing().equals(SCOPE_HEADING)) {
                inScope = true;
                found = true;
            }
        }
        if (!found) {
            errors.add(new ValidationError("REQUIRED_SECTION", "缺少必要章節「" + SCOPE_HEADING + "」"));
        } else if (!hasBody) {
            errors.add(new ValidationError("REQUIRED_SECTION", "「" + SCOPE_HEADING + "」不可空白"));
        }
    }

    private void checkExamples(String content, List<ValidationError> errors) {
        List<String> questions = exampleQuestionParser.parse(content);
        if (questions.size() > MAX_EXAMPLES) {
            errors.add(new ValidationError("EXAMPLE_QUESTIONS",
                    "「範例問題」最多 " + MAX_EXAMPLES + " 題，目前有 " + questions.size() + " 題"));
        }
        if (questions.stream().anyMatch(q -> q.codePointCount(0, q.length()) > MAX_EXAMPLE_LENGTH)) {
            errors.add(new ValidationError("EXAMPLE_QUESTIONS", "「範例問題」每題最多 40 字"));
        }
    }

    private void checkForbidden(String[] lines, List<ValidationError> errors) {
        checkForbidden(null, lines, errors, true);
    }

    /** {@code field} names a single-line field (e.g. the name); null means the numbered content lines. */
    private void checkForbidden(String field, String[] lines, List<ValidationError> errors,
                                boolean numbered) {
        for (Forbidden f : forbidden) {
            for (int i = 0; i < lines.length; i++) {
                if (f.matches().test(lines[i])) {
                    String where = numbered ? "第 " + (i + 1) + " 行" : field;
                    errors.add(new ValidationError("FORBIDDEN_CONTENT",
                            where + "疑似包含" + f.label() + "，請移除"));
                    break;
                }
            }
        }
    }
}
