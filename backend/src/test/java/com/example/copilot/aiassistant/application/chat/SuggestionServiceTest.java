package com.example.copilot.aiassistant.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuggestionServiceTest {

    private static AssistantConfig instruction(String key, String content) {
        AssistantConfig c = new AssistantConfig();
        c.setConfigKey(key);
        c.setName(key);
        c.setConfigType(ConfigType.SYSTEM_INSTRUCTION);
        c.setContent(content);
        return c;
    }

    @Test
    void skipsCategoriesWithoutExampleQuestions() {
        PublishedConfigCache cache = mock(PublishedConfigCache.class);
        when(cache.current()).thenReturn(new PublishedConfigs(null, List.of(
                instruction("instruction.a", "## 適用問題\n甲\n\n## 範例問題\n- 問題一\n- 問題二"),
                instruction("instruction.b", "## 適用問題\n乙"),
                instruction("instruction.c", "## 適用問題\n丙\n\n## 範例問題\n說明文字，沒有條列"))));
        SuggestionService service = new SuggestionService(cache, new ExampleQuestionParser());

        List<SuggestionService.SuggestionCategory> result = service.suggestions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).configKey()).isEqualTo("instruction.a");
        assertThat(result.get(0).questions()).containsExactly("問題一", "問題二");
    }
}
