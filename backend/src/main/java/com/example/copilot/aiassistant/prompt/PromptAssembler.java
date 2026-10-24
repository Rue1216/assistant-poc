package com.example.copilot.aiassistant.prompt;

import com.example.copilot.aiassistant.config.AssistantConfig;
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

    public String systemMessage() {
        PublishedConfigCache.PublishedConfigs configs = cache.current();
        StringBuilder sb = new StringBuilder();
        if (configs.systemPrompt() != null) {
            sb.append(configs.systemPrompt().getContent().strip()).append("\n\n");
        } else if (warnedMissingMain.compareAndSet(false, true)) {
            log.warn("No published system.main found; continuing with instructions, safety rules and "
                    + "answer-format rules only");
        }
        sb.append("# 問題分類\n\n");
        for (AssistantConfig instruction : configs.instructions()) {
            sb.append("## ").append(instruction.getName()).append('\n')
                    .append(instruction.getContent().strip()).append("\n\n");
        }
        sb.append(SafetyRules.TEXT).append("\n\n").append(AnswerFormatRules.TEXT);
        return sb.toString();
    }
}
