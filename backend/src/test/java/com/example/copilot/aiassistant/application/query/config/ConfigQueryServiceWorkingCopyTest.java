package com.example.copilot.aiassistant.application.query.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.application.command.config.ConfigCommandUseCase;
import com.example.copilot.aiassistant.application.command.config.SaveCommand;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.SaveMode;
import com.example.copilot.aiassistant.domain.config.ValidationError;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.infrastructure.mongo.AiAssistantCollections;
import com.example.copilot.aiassistant.infrastructure.seed.SeedLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** The working-copy reads of {@link ConfigQueryService} against a real repository. */
class ConfigQueryServiceWorkingCopyTest extends IntegrationTestBase {

    private static final ConfigKey KEY = new ConfigKey("instruction.contact-prep");
    private static final String OTHER = "EEE11111";
    private static final String INVALID_CONTENT = "## 其他\n只有一個章節";

    @Autowired ConfigQueryService queryService;
    @Autowired ConfigCommandUseCase commands;
    @Autowired MongoTemplate mongo;
    @Autowired SeedLoader seedLoader;

    @AfterEach
    void restoreSeed() {
        mongo.remove(new Query(), AiAssistantCollections.CONFIG);
        seedLoader.run(new DefaultApplicationArguments());
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AssistantException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void validateWorkingCopyReportsRuleViolations() {
        assertError(() -> queryService.validateWorkingCopy(KEY), ErrorCode.AI_CONFIG_CONFLICT);
        assertError(() -> queryService.validateWorkingCopy(new ConfigKey("instruction.nope")),
                ErrorCode.AI_CONFIG_NOT_FOUND);

        AssistantConfig draft = commands.createDraft(KEY, OTHER);
        assertThat(queryService.findWorkingCopy(KEY)).hasValueSatisfying(wc -> assertThat(wc.version())
                .isEqualTo(draft.version()));
        assertThat(queryService.validateWorkingCopy(KEY)).isEmpty();
        commands.saveWorkingCopy(KEY, new SaveCommand("聯繫準備", INVALID_CONTENT, "note", SaveMode.KEEP_STATUS,
                draft.docVersion()), OTHER);

        assertThat(queryService.validateWorkingCopy(KEY)).extracting(ValidationError::rule)
                .contains("REQUIRED_SECTION");
    }
}
