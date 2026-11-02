package com.example.copilot.aiassistant.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.answer.AnswerAssembler;
import com.example.copilot.aiassistant.domain.audit.CredentialMasker;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.prompt.CustomerNotes;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import com.example.copilot.aiassistant.domain.prompt.PromptAssembler;
import com.example.copilot.aiassistant.domain.tool.ToolExecutor;
import com.example.copilot.aiassistant.domain.tool.ToolGuard;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class DomainBeanConfigurationTest extends IntegrationTestBase {

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private ApplicationContext context;

    @Test
    void registersAllFiveTools() {
        assertThat(registry.names()).containsExactlyInAnyOrder(
                "getCustomer", "getRealTimeCustomer", "getStockQuote", "getMarketIndex", "getCompanyAnnouncements");
    }

    @Test
    void domainBeansExist() {
        List<Class<?>> types = List.of(PiiRedactor.class, CredentialMasker.class, ExampleQuestionParser.class,
                CustomerNotes.class, PromptAssembler.class, AnswerAssembler.class, ToolRegistry.class,
                ToolGuard.class, ToolExecutor.class, ConfigValidator.class);
        for (Class<?> type : types) {
            assertThat(context.getBeansOfType(type)).as(type.getSimpleName()).hasSize(1);
        }
    }
}
