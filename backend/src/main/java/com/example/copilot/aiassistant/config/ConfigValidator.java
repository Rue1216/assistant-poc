package com.example.copilot.aiassistant.config;

import com.example.copilot.aiassistant.prompt.ExampleQuestionParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Pre-publish validation of prompt configs (spec 04 section 2.5). */
@Component
public class ConfigValidator {

    static final int MAX_BYTES = 65_536;
    static final int MIN_EXAMPLES = 1;
    static final int MAX_EXAMPLES = 5;
    static final int MAX_EXAMPLE_LENGTH = 40;
    private static final String EXAMPLE_HEADING = "## 範例問題";

    private static final List<String> PROMPT_SECTIONS = List.of("## 角色", "## 安全規則", "## 回答格式");
    private static final List<String> INSTRUCTION_SECTIONS =
            List.of("## 適用問題", "## 回答套版", EXAMPLE_HEADING, "## 注意事項");

    /** Spec 04 section 2.5 credential patterns; shared with the audit CredentialMasker. */
    public static final List<String> API_KEY_REGEXES =
            List.of("sk-[A-Za-z0-9_-]{16,}", "Bearer\\s+[A-Za-z0-9._-]{16,}");
    public static final String PASSWORD_REGEX = "(password|passwd|密碼)\\s*[:：=]";

    private record Forbidden(String label, Pattern pattern) {
        Forbidden(String label, String... regexes) {
            this(label, Pattern.compile(String.join("|", regexes), Pattern.CASE_INSENSITIVE));
        }
    }

    private static final List<Forbidden> FORBIDDEN = List.of(
            new Forbidden("API key 或 token", API_KEY_REGEXES.toArray(String[]::new)),
            new Forbidden("密碼", PASSWORD_REGEX),
            new Forbidden("網址或 IP", "https?://", "\\b\\d{1,3}(\\.\\d{1,3}){3}\\b"),
            new Forbidden("身分證號", "\\b[A-Z][12]\\d{8}\\b"),
            new Forbidden("手機號碼", "\\b09\\d{2}-?\\d{3}-?\\d{3}\\b"),
            new Forbidden("顧客編號", "\\bC\\d{3}\\b"),
            new Forbidden("越權指令", "忽略(以上|先前|所有).{0,10}(規則|指示|限制)",
                    "ignore (all|previous|above) (rules|instructions)"));

    private final ExampleQuestionParser exampleQuestionParser;

    public ConfigValidator(ExampleQuestionParser exampleQuestionParser) {
        this.exampleQuestionParser = exampleQuestionParser;
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
        boolean hasExampleSection = true;
        for (String heading : type == ConfigType.SYSTEM_PROMPT ? PROMPT_SECTIONS : INSTRUCTION_SECTIONS) {
            if (!hasHeading(lines, heading)) {
                errors.add(new ValidationError("REQUIRED_SECTION", "缺少必要章節「" + heading + "」"));
                if (heading.equals(EXAMPLE_HEADING)) {
                    hasExampleSection = false;
                }
            }
        }
        if (type == ConfigType.SYSTEM_INSTRUCTION && hasExampleSection) {
            checkExamples(content, errors);
        }
        checkForbidden(lines, errors);
        return errors;
    }

    private static boolean hasHeading(String[] lines, String heading) {
        for (String line : lines) {
            if (line.stripTrailing().equals(heading)) {
                return true;
            }
        }
        return false;
    }

    private void checkExamples(String content, List<ValidationError> errors) {
        List<String> questions = exampleQuestionParser.parse(content);
        if (questions.size() < MIN_EXAMPLES || questions.size() > MAX_EXAMPLES) {
            errors.add(new ValidationError("EXAMPLE_QUESTIONS",
                    "「範例問題」需有 1 至 5 題，目前有 " + questions.size() + " 題"));
        }
        if (questions.stream().anyMatch(q -> q.codePointCount(0, q.length()) > MAX_EXAMPLE_LENGTH)) {
            errors.add(new ValidationError("EXAMPLE_QUESTIONS", "「範例問題」每題最多 40 字"));
        }
    }

    private static void checkForbidden(String[] lines, List<ValidationError> errors) {
        checkForbidden(null, lines, errors, true);
    }

    /** {@code field} names a single-line field (e.g. the name); null means the numbered content lines. */
    private static void checkForbidden(String field, String[] lines, List<ValidationError> errors,
                                       boolean numbered) {
        for (Forbidden f : FORBIDDEN) {
            for (int i = 0; i < lines.length; i++) {
                if (f.pattern().matcher(lines[i]).find()) {
                    String where = numbered ? "第 " + (i + 1) + " 行" : field;
                    errors.add(new ValidationError("FORBIDDEN_CONTENT",
                            where + "疑似包含" + f.label() + "，請移除"));
                    break;
                }
            }
        }
    }
}
