package com.example.copilot.aiassistant.prompt;

import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.prompt.PublishedConfigCache.PublishedConfigs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the system message: system.main, then instructions, then the code-only safety rules (01 section 4) and
 * answer format rules (answer-citations spec 5).
 */
@Component
public class PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(PromptAssembler.class);

    private final PublishedConfigCache cache;
    private final AtomicBoolean warnedMissingMain = new AtomicBoolean();

    public PromptAssembler(PublishedConfigCache cache) {
        this.cache = cache;
    }

    /** Assembles the prompt from the cached published configs. */
    public AssembledPrompt current() {
        return assemble(cache.current());
    }

    /** Assembles the prompt from the given configs; instructions are ordered by configKey here. */
    public AssembledPrompt assemble(PublishedConfigs configs) {
        List<PromptVersion> versions = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        AssistantConfig main = configs.systemPrompt();
        if (main != null) {
            sb.append(main.getContent().strip()).append("\n\n");
            versions.add(new PromptVersion(main.getConfigKey(), main.getVersion()));
        } else if (warnedMissingMain.compareAndSet(false, true)) {
            log.warn("No published system.main found; continuing with instructions, safety rules and "
                    + "answer-format rules only");
        }
        sb.append("# 問題分類\n\n");
        List<AssistantConfig> instructions = configs.instructions().stream()
                .sorted(Comparator.comparing(AssistantConfig::getConfigKey)).toList();
        for (AssistantConfig instruction : instructions) {
            sb.append("## ").append(instruction.getName()).append('\n')
                    .append(instruction.getContent().strip()).append("\n\n");
            versions.add(new PromptVersion(instruction.getConfigKey(), instruction.getVersion()));
        }
        sb.append(SafetyRules.TEXT).append("\n\n").append(AnswerFormatRules.TEXT);
        versions.sort(Comparator.comparing(PromptVersion::configKey));
        return new AssembledPrompt(sb.toString(), versions);
    }
}
