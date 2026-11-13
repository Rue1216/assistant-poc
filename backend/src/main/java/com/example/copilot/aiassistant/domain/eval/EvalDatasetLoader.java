package com.example.copilot.aiassistant.domain.eval;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Parses and validates {@code eval-cases.yml}. SafeConstructor yields only basic types, so the file can never
 * instantiate classes. All problems are collected and reported in one exception, each as
 * {@code eval-cases.yml: <id or #index>.<field>: <reason>}. Unknown keys are errors and duplicate keys fail parsing.
 */
public final class EvalDatasetLoader {

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9-]{1,40}$");
    private static final int GROUP_MAX = 20;
    private static final int CERT_NO_MAX = 12;
    private static final int QUESTION_MAX = 1000;
    private static final int PHRASE_MAX = 50;
    private static final int PHRASES_MAX_ITEMS = 10;
    private static final int RUBRIC_MAX_ITEMS = 5;
    private static final int RUBRIC_ITEM_MAX = 100;
    /** Only these keys are allowed: a misspelt key (mustNotContian) would otherwise silently disable a check. */
    private static final Set<String> CASE_KEYS = Set.of("id", "group", "accessLevel", "certNo", "question",
            "expect", "rubric");
    private static final Set<String> EXPECT_KEYS = Set.of("tools", "forbiddenTools", "refusal", "mustContain",
            "mustNotContain", "category", "toolOrder");
    private static final Pattern CERT_NO = Pattern.compile(CertNo.PATTERN);
    private static final Pattern CATEGORY = Pattern.compile("^(instruction\\.[a-z0-9-]{1,50}|none)$");

    private EvalDatasetLoader() {
    }

    public static EvalDataset parse(byte[] yaml, Set<String> toolNames, int maxCases) {
        Object root;
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            root = new Yaml(new SafeConstructor(options)).load(new String(yaml, StandardCharsets.UTF_8));
        } catch (DuplicateKeyException e) {
            int line = e.getProblemMark() == null ? 0 : e.getProblemMark().getLine() + 1;
            throw new IllegalStateException("eval-cases.yml: 第 " + line + " 行: 重複的欄位", e);
        } catch (YAMLException e) {
            throw new IllegalStateException("eval-cases.yml: 無法解析 YAML", e);
        }
        if (!(root instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalStateException("eval-cases.yml: 內容必須是至少 1 題的清單");
        }

        List<String> errors = new ArrayList<>();
        if (items.size() > maxCases) {
            errors.add("eval-cases.yml: 題數 " + items.size() + " 超過上限，最多 " + maxCases + " 題");
        }
        List<EvalCase> cases = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            EvalCase parsed = parseCase(items.get(i), i + 1, toolNames, seenIds, errors);
            if (parsed != null) {
                cases.add(parsed);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(String.join("\n", errors));
        }
        return new EvalDataset(cases, sha256(yaml));
    }

    private static EvalCase parseCase(Object raw, int index, Set<String> toolNames, Set<String> seenIds,
                                      List<String> errors) {
        String label = "#" + index;
        if (!(raw instanceof Map<?, ?> map)) {
            errors.add(msg(label, "(題目)", "必須是物件"));
            return null;
        }
        int before = errors.size();

        Object idValue = map.get("id");
        if (idValue instanceof String s && ID.matcher(s).matches()) {
            label = s;
            if (!seenIds.add(s)) {
                errors.add(msg(label, "id", "重複"));
            }
        } else {
            errors.add(msg(label, "id", "必須符合 [A-Za-z0-9-]{1,40}"));
        }
        unknownKeys(map, CASE_KEYS, label, "", errors);

        String group = text(map.get("group"), GROUP_MAX, label, "group", errors);
        AccessLevel level = accessLevel(map.get("accessLevel"), label, errors);
        String certNo = text(map.get("certNo"), CERT_NO_MAX, label, "certNo", errors);
        if (certNo != null && !CERT_NO.matcher(certNo).matches()) {
            errors.add(msg(label, "certNo", "必須符合 [A-Z0-9&]{8,12}"));
        }
        String question = text(map.get("question"), QUESTION_MAX, label, "question", errors);

        EvalCase.Expect expect = null;
        if (map.get("expect") instanceof Map<?, ?> e) {
            expect = parseExpect(e, label, toolNames, errors);
        } else {
            errors.add(msg(label, "expect", "必填，且必須是物件"));
        }
        List<String> rubric = list(map.get("rubric"), 1, RUBRIC_MAX_ITEMS, RUBRIC_ITEM_MAX, label, "rubric", errors);

        if (errors.size() > before) {
            return null;
        }
        return new EvalCase((String) idValue, group, level, certNo, question, expect, rubric);
    }

    private static EvalCase.Expect parseExpect(Map<?, ?> e, String label, Set<String> toolNames,
                                               List<String> errors) {
        unknownKeys(e, EXPECT_KEYS, label, "expect.", errors);
        List<String> tools = toolList(e.get("tools"), "expect.tools", label, toolNames, errors);
        List<String> forbidden = toolList(e.get("forbiddenTools"), "expect.forbiddenTools", label, toolNames,
                errors);
        for (String name : tools) {
            if (forbidden.contains(name)) {
                errors.add(msg(label, "expect.forbiddenTools", "與 expect.tools 重複：" + name));
            }
        }
        List<String> toolOrder = toolList(e.get("toolOrder"), "expect.toolOrder", label, toolNames, errors);
        for (String name : toolOrder) {
            if (forbidden.contains(name)) {
                errors.add(msg(label, "expect.toolOrder", "與 expect.forbiddenTools 重複：" + name));
            }
        }
        boolean refusal = false;
        if (e.get("refusal") instanceof Boolean b) {
            refusal = b;
        } else {
            errors.add(msg(label, "expect.refusal", "必填，且必須是 true 或 false"));
        }
        List<String> mustContain = list(e.get("mustContain"), 0, PHRASES_MAX_ITEMS, PHRASE_MAX, label,
                "expect.mustContain", errors);
        List<String> mustNotContain = list(e.get("mustNotContain"), 0, PHRASES_MAX_ITEMS, PHRASE_MAX, label,
                "expect.mustNotContain", errors);
        String category = null;
        if (e.containsKey("category")) {
            if (e.get("category") instanceof String s && CATEGORY.matcher(s).matches()) {
                category = s;
            } else {
                errors.add(msg(label, "expect.category", "必須是 instruction.<名稱> 或 none"));
            }
        }
        return new EvalCase.Expect(tools, forbidden, refusal, mustContain, mustNotContain, category,
                toolOrder);
    }

    private static List<String> toolList(Object value, String field, String label, Set<String> toolNames,
                                         List<String> errors) {
        List<String> names = list(value, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, label, field, errors);
        for (String name : names) {
            if (!toolNames.contains(name)) {
                errors.add(msg(label, field, "未知的工具：" + name));
            }
        }
        return names;
    }

    private static void unknownKeys(Map<?, ?> map, Set<String> allowed, String label, String prefix,
                                    List<String> errors) {
        for (Object key : map.keySet()) {
            if (!allowed.contains(key)) {
                errors.add(msg(label, prefix + key, "未知的欄位"));
            }
        }
    }

    private static AccessLevel accessLevel(Object value, String label, List<String> errors) {
        if ("FULL".equals(value)) {
            return AccessLevel.FULL;
        }
        if ("LIMITED".equals(value)) {
            return AccessLevel.LIMITED;
        }
        errors.add(msg(label, "accessLevel", "必須是 FULL 或 LIMITED"));
        return null;
    }

    private static String text(Object value, int max, String label, String field, List<String> errors) {
        if (!(value instanceof String s)) {
            errors.add(msg(label, field, "必填，且必須是文字"));
            return null;
        }
        if (s.isEmpty() || s.length() > max) {
            errors.add(msg(label, field, "長度必須為 1–" + max + " 字"));
        }
        return s;
    }

    /** A missing value is an empty list; items must be non-empty strings of at most {@code itemMax} chars. */
    private static List<String> list(Object value, int minItems, int maxItems, int itemMax, String label,
                                     String field, List<String> errors) {
        if (value == null) {
            if (minItems > 0) {
                errors.add(msg(label, field, "至少 " + minItems + " 項"));
            }
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            errors.add(msg(label, field, "必須是清單"));
            return List.of();
        }
        if (raw.size() < minItems || raw.size() > maxItems) {
            errors.add(msg(label, field, "項數必須為 " + minItems + "–" + maxItems + " 項"));
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i) instanceof String s && !s.isEmpty() && s.length() <= itemMax) {
                out.add(s);
            } else {
                errors.add(msg(label, field + "[" + (i + 1) + "]", "必須是 1–" + itemMax + " 字的文字"));
            }
        }
        return out;
    }

    private static String msg(String label, String field, String reason) {
        return "eval-cases.yml: " + label + "." + field + ": " + reason;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
