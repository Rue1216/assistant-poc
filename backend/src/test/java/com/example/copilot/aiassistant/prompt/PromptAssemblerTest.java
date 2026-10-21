package com.example.copilot.aiassistant.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.config.AssistantConfig;
import com.example.copilot.aiassistant.config.AssistantConfigRepository;
import com.example.copilot.aiassistant.config.ConfigStatus;
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
        String message = assembler.systemMessage();

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
        String message = assembler.systemMessage();

        assertThat(message).contains("## 顧客概況", "## 經營機會", "## 聯繫準備", "## 聯繫話題", "## 市場快訊");
    }

    @Test
    void safetyRulesAreLastEvenIfSystemMainIsEdited() {
        AssistantConfig main = repository.findByConfigKeyAndStatus("system.main", ConfigStatus.PUBLISHED)
                .orElseThrow();
        originalContent = main.getContent();
        main.setContent(originalContent.replaceAll("(?s)## 安全規則.*?(?=\n## |\\z)", ""));
        repository.save(main);
        cache.invalidate();

        String message = assembler.systemMessage();

        assertThat(message).doesNotContain("## 安全規則");
        assertThat(message).endsWith(SafetyRules.TEXT);
    }

    @Test
    void safetyRulesContainAllSevenRules() {
        assertThat(SafetyRules.TEXT).startsWith("# 不可覆寫的安全規則");
        assertThat(SafetyRules.TEXT.lines().filter(l -> l.startsWith("- "))).hasSize(7);
    }
}
