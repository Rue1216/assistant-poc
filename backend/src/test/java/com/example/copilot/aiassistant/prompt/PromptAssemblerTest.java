package com.example.copilot.aiassistant.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.config.AssistantConfigRepository;
import com.example.copilot.aiassistant.config.ConfigStatus;
import com.example.copilot.aiassistant.prompt.PublishedConfigCache.PublishedConfigs;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PromptAssemblerTest extends IntegrationTestBase {

    @Autowired
    PromptAssembler assembler;
    @Autowired
    PublishedConfigCache cache;
    @Autowired
    AssistantConfigRepository repository;

    private String originalContent;

    @AfterEach
    void restoreSystemMain() {
        if (originalContent != null) {
            AssistantConfig main = repository.findByConfigKeyAndStatus("system.main", ConfigStatus.PUBLISHED)
                    .orElseThrow();
            main.setContent(originalContent);
            repository.save(main);
            originalContent = null;
        }
        cache.invalidate();
    }

    @Test
    void systemMessageOrder() {
        cache.invalidate();
        String message = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), null).text();

        int role = message.indexOf("## 角色");
        int categories = message.indexOf("# 問題分類");
        int safety = message.indexOf("# 不可覆寫的安全規則");
        assertThat(role).isGreaterThanOrEqualTo(0);
        assertThat(categories).isGreaterThan(role);
        assertThat(safety).isGreaterThan(categories);
    }

    @Test
    void includesAllPublishedInstructions() {
        cache.invalidate();
        String message = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), null).text();

        assertThat(message).contains("## 顧客概況", "## 資產負債", "## 會員等級", "## 商品機會", "## 往來與通路",
                "## 聯繫準備", "## 市場快訊");
    }

    @Test
    void safetyRulesAreLastEvenIfSystemMainIsEdited() {
        AssistantConfig main = repository.findByConfigKeyAndStatus("system.main", ConfigStatus.PUBLISHED)
                .orElseThrow();
        originalContent = main.getContent();
        main.setContent(originalContent.replaceAll("(?s)## 安全規則.*?(?=\n## |\\z)", ""));
        repository.save(main);
        cache.invalidate();

        String message = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), null).text();

        assertThat(message).doesNotContain("## 安全規則");
        assertThat(message).contains(SafetyRules.TEXT + "\n\n" + AnswerFormatRules.TEXT + "\n\n# 目前日期\n今天是 ");
    }

    @Test
    void safetyRulesContainAllSevenRules() {
        assertThat(SafetyRules.TEXT).startsWith("# 不可覆寫的安全規則");
        assertThat(SafetyRules.TEXT.lines().filter(l -> l.startsWith("- "))).hasSize(7);
    }

    @Test
    void formatRulesFollowSafetyRulesAtTheEnd() {
        cache.invalidate();
        String message = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), null).text();

        assertThat(message).contains(SafetyRules.TEXT + "\n\n" + AnswerFormatRules.TEXT + "\n\n# 目前日期\n今天是 ");
    }

    @Test
    void dateSectionIsLastAndUsesTaipeiZone() {
        // 17:30 UTC on 10-26 is already 10-27 in Taipei (UTC+8).
        PromptAssembler fixed = new PromptAssembler(null,
                Clock.fixed(Instant.parse("2026-10-26T17:30:00Z"), ZoneOffset.UTC));
        String text = fixed.assemble(cache.loadFresh(), InstructionSelection.all(), null).text();

        assertThat(text).endsWith(AnswerFormatRules.TEXT + "\n\n# 目前日期\n今天是 2026/10/27（台北時間）");
        int main = text.indexOf("## 角色");
        int categories = text.indexOf("# 問題分類");
        int safety = text.indexOf("# 不可覆寫的安全規則");
        int format = text.indexOf("# 回答格式規則");
        int date = text.indexOf("# 目前日期");
        assertThat(main).isGreaterThanOrEqualTo(0);
        assertThat(categories).isGreaterThan(main);
        assertThat(safety).isGreaterThan(categories);
        assertThat(format).isGreaterThan(safety);
        assertThat(date).isGreaterThan(format);
    }


    @Test
    void dateIsWrittenAsYyyyMmDd() {
        PromptAssembler fixed = new PromptAssembler(null,
                Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), ZoneOffset.UTC));
        String text = fixed.assemble(new PublishedConfigs(null, List.of()), InstructionSelection.all(), null).text();

        assertThat(text).contains("今天是 2026/10/28（台北時間）");
    }

    @Test
    void customerNotesSitAfterTheLastCategoryAndBeforeTheSafetyRules() {
        cache.invalidate();
        String notes = "# 本顧客註記\n- 客訴";
        String text = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), notes).text();

        int lastCategory = text.lastIndexOf("\n## ");
        int notesAt = text.indexOf(notes);
        int safety = text.indexOf("# 不可覆寫的安全規則");
        assertThat(notesAt).isGreaterThan(lastCategory);
        assertThat(safety).isGreaterThan(notesAt);
        assertThat(text).contains(notes + "\n\n# 不可覆寫的安全規則");
    }

    @Test
    void noNotesSectionWhenNull() {
        cache.invalidate();
        String text = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), null).text();

        assertThat(text).doesNotContain("# 本顧客註記");
    }

    @Test
    void notesDoNotChangeTheVersions() {
        cache.invalidate();
        AssembledPrompt without = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), null);
        AssembledPrompt with = assembler.assemble(assembler.snapshot(), InstructionSelection.all(),
                "# 本顧客註記\n- 無");

        assertThat(with.versions()).isEqualTo(without.versions());
    }

    @Test
    void safetyRulesCarryTheInvestmentAdviceWording() {
        assertThat(SafetyRules.TEXT).contains(
                "- 不自行提供投資建議；可轉述系統產生的推薦產品與意圖分析，但不得保證報酬或建議買賣時點");
    }
    @Test
    void formatRulesText() {
        assertThat(AnswerFormatRules.TEXT).startsWith("# 回答格式規則");
        assertThat(AnswerFormatRules.TEXT.lines().filter(l -> l.startsWith("- "))).hasSize(6);
        assertThat(AnswerFormatRules.TEXT).contains("不可只照抄套版文字");
    }

    @Test
    void versionsListEveryPublishedConfigSortedByKey() {
        cache.invalidate();
        AssembledPrompt prompt = assembler.assemble(assembler.snapshot(), InstructionSelection.all(), null);
        assertThat(prompt.versions()).extracting(PromptVersion::configKey).containsExactly(
                "instruction.asset-liability", "instruction.contact-prep", "instruction.customer-overview",
                "instruction.engagement", "instruction.market-brief", "instruction.member-rank",
                "instruction.opportunity", "system.main");
        assertThat(prompt.versions()).allSatisfy(v -> assertThat(v.version()).isPositive());
    }

    @Test
    void assembleUsesGivenConfigs() {
        PublishedConfigs fresh = cache.loadFresh();
        AssistantConfig replaced = copyOf(fresh.instructions().get(0));
        PublishedConfigs given = new PublishedConfigs(fresh.systemPrompt(),
                Stream.concat(Stream.of(replaced), fresh.instructions().stream().skip(1)).toList());
        AssembledPrompt prompt = assembler.assemble(given, InstructionSelection.all(), null);
        assertThat(prompt.text()).contains("替換後內容").contains("# 不可覆寫的安全規則");
        assertThat(prompt.versions()).contains(new PromptVersion(replaced.getConfigKey(), 99));
    }

    @Test
    void assembleWithoutSystemPromptStillHasRules() {
        AssembledPrompt prompt = assembler.assemble(new PublishedConfigs(null, List.of()), InstructionSelection.all(), null);
        assertThat(prompt.text()).contains("# 問題分類", "# 不可覆寫的安全規則", "# 回答格式規則");
        assertThat(prompt.versions()).isEmpty();
    }

    @Test
    void onlyLoadsTheSelectedInstruction() {
        cache.invalidate();
        AssembledPrompt prompt = assembler.assemble(assembler.snapshot(),
                InstructionSelection.only("instruction.contact-prep"), null);

        assertThat(prompt.text()).contains("## 聯繫準備")
                .doesNotContain("## 顧客概況", "## 商品機會", "## 往來與通路", "## 市場快訊")
                .doesNotContain("（本題沒有適用的問題分類）");
        assertThat(prompt.versions()).extracting(PromptVersion::configKey)
                .containsExactly("instruction.contact-prep", "system.main");
    }

    @Test
    void noneLoadsNoInstructionAndSaysSo() {
        cache.invalidate();
        AssembledPrompt prompt = assembler.assemble(assembler.snapshot(), InstructionSelection.none(), null);

        assertThat(prompt.text()).contains("# 問題分類\n\n（本題沒有適用的問題分類）\n\n# 不可覆寫的安全規則")
                .doesNotContain("## 聯繫準備", "## 市場快訊");
        assertThat(prompt.versions()).extracting(PromptVersion::configKey).containsExactly("system.main");
    }

    @Test
    void snapshotReturnsTheCachedConfigs() {
        cache.invalidate();
        assertThat(assembler.snapshot()).isSameAs(cache.current());
    }

    private static AssistantConfig copyOf(AssistantConfig source) {
        AssistantConfig copy = new AssistantConfig();
        copy.setConfigKey(source.getConfigKey());
        copy.setConfigType(source.getConfigType());
        copy.setName(source.getName());
        copy.setStatus(source.getStatus());
        copy.setContent("## 適用問題\n替換後內容");
        copy.setVersion(99);
        return copy;
    }
}
