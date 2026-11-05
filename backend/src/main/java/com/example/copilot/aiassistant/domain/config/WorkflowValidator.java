package com.example.copilot.aiassistant.domain.config;

import com.example.copilot.aiassistant.domain.prompt.WorkflowParser;
import com.example.copilot.aiassistant.domain.prompt.WorkflowParser.Marker;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.ToolOption;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Pre-publish check of inline tool markers. Reports structure only and never throws on odd content. */
public class WorkflowValidator {

    public static final int MAX_STEPS = 3;
    private static final String RULE = "WORKFLOW_INVALID";
    private static final int CLIP = 32;
    private static final List<String> MARKER_FREE_SECTIONS = List.of("## 適用問題", "## 範例問題");
    private static final Pattern COMPLETE_MARKER = Pattern.compile("\\{\\{[^{}\\r\\n]*\\}\\}");

    private final ToolRegistry registry;

    public WorkflowValidator(ToolRegistry registry) {
        this.registry = registry;
    }

    public List<ValidationError> validate(ConfigType type, String content) {
        List<ValidationError> errors = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return errors;
        }
        List<Marker> markers = WorkflowParser.markers(content);
        if (type == ConfigType.SYSTEM_PROMPT) {
            if (!markers.isEmpty()) {
                errors.add(error("System Prompt 不可放工具標記，流程要設定在問題分類"));
            }
            return errors;
        }
        String[] lines = content.split("\n", -1);
        String[] sectionOfLine = sections(lines);
        checkUnclosed(lines, errors);
        Set<String> blocked = new LinkedHashSet<>();
        List<Marker> checked = new ArrayList<>();
        List<Integer> ordinals = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            String section = sectionOfLine[Math.min(marker.line(), lines.length) - 1];
            if (MARKER_FREE_SECTIONS.contains(section)) {
                if (blocked.add(section)) {
                    errors.add(error("「" + section + "」不可放工具標記（這段會交給路由或顯示成快捷按鈕）"));
                }
            } else {
                checked.add(marker);
                ordinals.add(i + 1);
            }
        }
        checkMarkers(checked, ordinals, errors);
        return errors;
    }

    /** The "## " heading each line sits under; a heading line belongs to its own section. */
    private static String[] sections(String[] lines) {
        String[] result = new String[lines.length];
        String current = "";
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("## ")) {
                current = lines[i].stripTrailing();
            }
            result[i] = current;
        }
        return result;
    }

    /** A "{{" left once the complete markers are removed has no closing braces. */
    private static void checkUnclosed(String[] lines, List<ValidationError> errors) {
        for (int i = 0; i < lines.length; i++) {
            if (COMPLETE_MARKER.matcher(lines[i]).replaceAll("").contains("{{")) {
                errors.add(error("第 " + (i + 1) + " 行的工具標記沒有結尾"));
            }
        }
    }

    private void checkMarkers(List<Marker> markers, List<Integer> ordinals, List<ValidationError> errors) {
        Set<String> tools = new LinkedHashSet<>();
        for (Marker marker : markers) {
            if (marker.wellFormed()) {
                tools.add(marker.toolName());
            }
        }
        if (tools.size() > MAX_STEPS) {
            errors.add(error("工具標記最多 " + MAX_STEPS + " 個不同的工具，目前有 " + tools.size() + " 個"));
        }
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            String prefix = "第 " + ordinals.get(i) + " 個工具標記";
            if (!marker.wellFormed()) {
                errors.add(error(prefix + "格式不正確"));
                continue;
            }
            Optional<AssistantTool<?>> tool = registry.find(marker.toolName());
            if (tool.isEmpty()) {
                errors.add(error(prefix + "：找不到工具 " + clip(marker.toolName())));
                continue;
            }
            checkOptions(prefix, tool.get(), marker, errors);
        }
    }

    private static void checkOptions(String prefix, AssistantTool<?> tool, Marker marker,
                                     List<ValidationError> errors) {
        String name = clip(tool.name());
        List<String> keys = tool.options().stream().map(ToolOption::key).toList();
        if (keys.isEmpty()) {
            if (!marker.options().isEmpty()) {
                errors.add(error(prefix + "：" + name + " 不接受選項"));
            }
        } else if (marker.options().isEmpty()) {
            errors.add(error(prefix + "：" + name + " 至少要選一個選項"));
        } else {
            for (String option : marker.options()) {
                if (!keys.contains(option)) {
                    errors.add(error(prefix + "：" + name + " 沒有選項 " + clip(option)));
                }
            }
        }
    }

    /** Admin text is echoed back in messages, so cap what one marker can add. */
    private static String clip(String text) {
        return text.codePointCount(0, text.length()) <= CLIP ? text
                : text.substring(0, text.offsetByCodePoints(0, CLIP));
    }

    private static ValidationError error(String message) {
        return new ValidationError(RULE, message);
    }
}
