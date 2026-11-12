package com.example.copilot.aiassistant.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import org.junit.jupiter.api.Test;

class ConfigKeyTest {

    private static void assertInvalid(Runnable action, String field) {
        AssistantException e = catchThrowableOfType(AssistantException.class, action::run);
        assertThat(e).isNotNull();
        assertThat(e.code()).isEqualTo(ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail(field));
    }

    @Test void rejectsNull() { assertInvalid(() -> new ConfigKey(null), "configKey"); }

    @Test void acceptsValid() { assertThat(new ConfigKey("instruction.asset-liability").value()).isEqualTo("instruction.asset-liability"); }
    @Test void rejectsInvalid() { assertInvalid(() -> new ConfigKey("Instruction.X"), "configKey"); }
    @Test void systemMainIsSystemPrompt() {
        assertThat(ConfigKey.SYSTEM_MAIN.isSystemPrompt()).isTrue();
        assertThat(ConfigKey.SYSTEM_MAIN.isInstruction()).isFalse();
        assertThat(ConfigKey.SYSTEM_MAIN.value()).isEqualTo("system.main");
    }
    @Test void instructionKeyIsInstruction() {
        ConfigKey k = new ConfigKey("instruction.opportunity");
        assertThat(k.isInstruction()).isTrue();
        assertThat(k.isSystemPrompt()).isFalse();
    }
    @Test void toStringIsValue() { assertThat(new ConfigKey("system.main").toString()).isEqualTo("system.main"); }
}
