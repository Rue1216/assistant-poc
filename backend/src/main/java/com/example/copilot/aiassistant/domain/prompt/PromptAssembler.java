package com.example.copilot.aiassistant.domain.prompt;

import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.PromptVersion;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the system message: system.main, then instructions, the code-only customer notes, then the code-only safety rules (01 section 4) and
 * answer format rules (answer-citations spec 5), and finally today's date in Taipei time.
 */
public class PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(PromptAssembler.class);

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final String NO_INSTRUCTION = "（本題沒有適用的問題分類）";

    private final Clock clock;
    private final AtomicBoolean warnedMissingMain = new AtomicBoolean();

    public PromptAssembler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Assembles the prompt from the given configs; instructions are ordered by configKey here. Only the selected
     * instructions are loaded and listed in the versions. A non-null {@code customerNotes} (see
     * {@link CustomerNotes}) goes between the instructions and the safety rules.
     */
    public AssembledPrompt assemble(PublishedConfigs configs, InstructionSelection selection, String customerNotes) {
        List<PromptVersion> versions = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        AssistantConfig main = configs.systemPrompt();
        if (main != null) {
            sb.append(main.content().strip()).append("\n\n");
            versions.add(new PromptVersion(main.configKey(), main.version()));
        } else if (warnedMissingMain.compareAndSet(false, true)) {
            log.warn("No published system.main found; continuing with instructions, safety rules and "
                    + "answer-format rules only");
        }
        sb.append("# 問題分類\n\n");
        List<AssistantConfig> instructions = configs.instructions().stream()
                .filter(i -> switch (selection.kind()) {
                    case ALL -> true;
                    case NONE -> false;
                    case ONLY -> i.configKey().equals(selection.configKey());
                })
                .sorted(Comparator.comparing(AssistantConfig::configKey)).toList();
        if (selection.kind() == InstructionSelection.Kind.NONE) {
            sb.append(NO_INSTRUCTION).append("\n\n");
        }
        for (AssistantConfig instruction : instructions) {
            sb.append("## ").append(instruction.name()).append('\n')
                    .append(instruction.content().strip()).append("\n\n");
            versions.add(new PromptVersion(instruction.configKey(), instruction.version()));
        }
        if (customerNotes != null) {
            sb.append(customerNotes).append("\n\n");
        }
        sb.append(SafetyRules.TEXT).append("\n\n").append(AnswerFormatRules.TEXT)
                .append("\n\n# 目前日期\n今天是 ").append(LocalDate.now(clock.withZone(TAIPEI)).format(DATE_FORMAT))
                .append("（台北時間）");
        versions.sort(Comparator.comparing(PromptVersion::configKey));
        return new AssembledPrompt(sb.toString(), versions);
    }
}
