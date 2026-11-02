package com.example.copilot.aiassistant.infrastructure.config;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.answer.AnswerAssembler;
import com.example.copilot.aiassistant.domain.audit.CredentialMasker;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.prompt.CustomerNotes;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import com.example.copilot.aiassistant.domain.prompt.PromptAssembler;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.ToolExecutor;
import com.example.copilot.aiassistant.domain.tool.ToolGuard;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.aiassistant.domain.tool.customer.GetCustomerTool;
import com.example.copilot.aiassistant.domain.tool.customer.GetRealTimeCustomerTool;
import com.example.copilot.aiassistant.domain.tool.market.GetCompanyAnnouncementsTool;
import com.example.copilot.aiassistant.domain.tool.market.GetMarketIndexTool;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool;
import com.example.copilot.aiassistant.domain.tool.market.TwseGateway;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Registers the framework-free domain classes as Spring beans. */
@Configuration(proxyBeanMethods = false)
public class DomainBeanConfiguration {

    @Bean
    PiiRedactor piiRedactor() {
        return new PiiRedactor();
    }

    @Bean
    CredentialMasker credentialMasker() {
        return new CredentialMasker();
    }

    @Bean
    ExampleQuestionParser exampleQuestionParser() {
        return new ExampleQuestionParser();
    }

    @Bean
    CustomerNotes customerNotes() {
        return new CustomerNotes();
    }

    @Bean
    PromptAssembler promptAssembler(Clock clock) {
        return new PromptAssembler(clock);
    }

    @Bean
    AnswerAssembler answerAssembler(PiiRedactor redactor, JsonMapper mapper) {
        return new AnswerAssembler(redactor, mapper);
    }

    @Bean
    ConfigValidator configValidator(ExampleQuestionParser parser, AiAssistantProperties props) {
        return new ConfigValidator(parser, props.config().allowedUrlHosts());
    }

    @Bean
    ToolRegistry toolRegistry(List<AssistantTool<?>> tools) {
        return new ToolRegistry(tools);
    }

    @Bean
    ToolGuard toolGuard(ToolRegistry registry, Validator validator, JsonMapper mapper) {
        return new ToolGuard(registry, validator, mapper);
    }

    @Bean(destroyMethod = "shutdown")
    ToolExecutor toolExecutor(ToolGuard guard, PiiRedactor redactor, JsonMapper mapper, AiAssistantProperties props) {
        return new ToolExecutor(guard, redactor, mapper, props.limits().toolTimeout(),
                props.limits().toolResultMaxBytes());
    }

    @Bean
    GetCustomerTool getCustomerTool(PiiRedactor redactor) {
        return new GetCustomerTool(redactor);
    }

    @Bean
    GetRealTimeCustomerTool getRealTimeCustomerTool(PiiRedactor redactor) {
        return new GetRealTimeCustomerTool(redactor);
    }

    @Bean
    GetStockQuoteTool getStockQuoteTool(TwseGateway twse) {
        return new GetStockQuoteTool(twse);
    }

    @Bean
    GetMarketIndexTool getMarketIndexTool(TwseGateway twse) {
        return new GetMarketIndexTool(twse);
    }

    @Bean
    GetCompanyAnnouncementsTool getCompanyAnnouncementsTool(TwseGateway twse) {
        return new GetCompanyAnnouncementsTool(twse);
    }
}
