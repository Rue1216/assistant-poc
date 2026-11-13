package com.example.copilot.aiassistant.application.query.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.application.chat.CustomerContextFactory;
import com.example.copilot.aiassistant.application.port.CustomerDataPort;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.PublishedConfigs;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SuggestionQueryServiceTest {

    private static AssistantConfig instruction(String key, String content) {
        return new AssistantConfig(null, new ConfigKey(key), ConfigType.SYSTEM_INSTRUCTION, key, content, null, 0, null, null, null,
                null, null, null, null, null);
    }

    private static final UserContext USER = new UserContext("U1", "user", "AO", Set.of());

    private static CustomerDataPort port() {
        CustomerDataPort port = mock(CustomerDataPort.class);
        when(port.findCustomer(any())).thenAnswer(i -> Optional.of(record(i.getArgument(0))));
        return port;
    }

    private static CustomerRecord record(String certNo) {
        return new CustomerRecord(certNo, "王小明", null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    @Test
    void noAccessIsCustomerNotFound() {
        PublishedConfigCache cache = mock(PublishedConfigCache.class);
        SuggestionQueryService service = new SuggestionQueryService(cache, new ExampleQuestionParser(),
                new CustomerContextFactory((u, c) -> AccessLevel.NONE, port()));

        assertThatThrownBy(() -> service.suggestions(new CertNo("A123456789"), USER))
                .isInstanceOfSatisfying(AssistantException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_CUSTOMER_NOT_FOUND));
    }

    @Test
    void skipsCategoriesWithoutExampleQuestions() {
        PublishedConfigCache cache = mock(PublishedConfigCache.class);
        CustomerDataPort port = port();
        when(cache.current()).thenReturn(new PublishedConfigs(null, List.of(
                instruction("instruction.a", "## 適用問題\n甲\n\n## 範例問題\n- 問題一\n- 問題二"),
                instruction("instruction.b", "## 適用問題\n乙"),
                instruction("instruction.c", "## 適用問題\n丙\n\n## 範例問題\n說明文字，沒有條列"))));
        SuggestionQueryService service = new SuggestionQueryService(cache, new ExampleQuestionParser(),
                new CustomerContextFactory((u, c) -> AccessLevel.FULL, port));

        List<SuggestionQueryService.SuggestionCategory> result = service.suggestions(new CertNo("A123456789"), USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).configKey()).isEqualTo("instruction.a");
        assertThat(result.get(0).questions()).containsExactly("問題一", "問題二");
    }
}
