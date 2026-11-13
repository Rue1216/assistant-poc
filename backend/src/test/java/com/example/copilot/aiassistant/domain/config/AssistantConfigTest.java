package com.example.copilot.aiassistant.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssistantConfigTest {

    private static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant AT = T0.plusSeconds(3600);
    private static final ConfigKey SYS = ConfigKey.SYSTEM_MAIN;
    private static final ConfigKey INS = new ConfigKey("instruction.opportunity");

    private static AssistantConfig stored(ConfigStatus status) {
        return new AssistantConfig("id1", INS, ConfigType.SYSTEM_INSTRUCTION, "n", "c", status, 3, 2, "note",
                "alice", T0, "alice", T0, status == ConfigStatus.SCHEDULED ? AT : null, 7L);
    }

    private static AssistantException thrown(Runnable r) {
        AssistantException e = catchThrowableOfType(AssistantException.class, r::run);
        assertThat(e).isNotNull();
        return e;
    }

    @Test void newKeyBuildsVersionOneDraft() {
        AssistantConfig c = AssistantConfig.newKey(INS, ConfigType.SYSTEM_INSTRUCTION, "n", "c", "alice", T0);
        assertThat(c.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(c.version()).isEqualTo(1);
        assertThat(c.id()).isNull();
        assertThat(c.docVersion()).isNull();
        assertThat(c.createdBy()).isEqualTo("alice");
        assertThat(c.updatedAt()).isEqualTo(T0);
    }

    @Test void newKeyAcceptsSystemPromptWithSystemKey() {
        assertThat(AssistantConfig.newKey(SYS, ConfigType.SYSTEM_PROMPT, "n", "", "a", T0).configType())
                .isEqualTo(ConfigType.SYSTEM_PROMPT);
    }

    @Test void newKeyRejectsSystemPromptWithInstructionKey() {
        AssistantException e = thrown(() -> AssistantConfig.newKey(INS, ConfigType.SYSTEM_PROMPT, "n", "", "a", T0));
        assertThat(e.code()).isEqualTo(ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail("configKey"));
    }

    @Test void newKeyRejectsInstructionWithSystemKey() {
        AssistantException e = thrown(
                () -> AssistantConfig.newKey(SYS, ConfigType.SYSTEM_INSTRUCTION, "n", "", "a", T0));
        assertThat(e.code()).isEqualTo(ErrorCode.AI_INPUT_INVALID);
        assertThat(e.details()).containsExactly(new FieldDetail("configKey"));
    }

    @Test void requireDocVersionAcceptsMatch() {
        assertThatCode(() -> stored(ConfigStatus.DRAFT).requireDocVersion(7L)).doesNotThrowAnyException();
    }

    @Test void requireDocVersionRejectsMismatchAndNull() {
        assertThat(thrown(() -> stored(ConfigStatus.DRAFT).requireDocVersion(8L)).code())
                .isEqualTo(ErrorCode.AI_CONFIG_CONFLICT);
        assertThat(thrown(() -> stored(ConfigStatus.DRAFT).requireDocVersion(null)).code())
                .isEqualTo(ErrorCode.AI_CONFIG_CONFLICT);
    }

    @Test void discardTurnsDraftIntoDiscarded() {
        AssistantConfig d = stored(ConfigStatus.DRAFT).discard("bob", T1);
        assertThat(d.status()).isEqualTo(ConfigStatus.DISCARDED);
        assertThat(d.scheduledAt()).isNull();
        assertThat(d.updatedBy()).isEqualTo("bob");
        assertThat(d.updatedAt()).isEqualTo(T1);
    }

    @Test void requireDiscardableRejectsNonDraftAndAcceptsDraft() {
        assertThatCode(() -> stored(ConfigStatus.DRAFT).requireDiscardable()).doesNotThrowAnyException();
        assertThat(thrown(() -> stored(ConfigStatus.SCHEDULED).requireDiscardable()).code())
                .isEqualTo(ErrorCode.AI_CONFIG_INVALID_STATE);
    }

    @Test void discardRejectsNonDraft() {
        for (ConfigStatus s : new ConfigStatus[] {ConfigStatus.SCHEDULED, ConfigStatus.PUBLISHED,
                ConfigStatus.ARCHIVED, ConfigStatus.DISCARDED}) {
            assertThat(thrown(() -> stored(s).discard("bob", T1)).code()).isEqualTo(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
    }

    @Test void requireApplicableAcceptsPublishedArchivedDiscarded() {
        for (ConfigStatus s : new ConfigStatus[] {ConfigStatus.PUBLISHED, ConfigStatus.ARCHIVED,
                ConfigStatus.DISCARDED}) {
            assertThatCode(() -> stored(s).requireApplicable()).doesNotThrowAnyException();
        }
    }

    @Test void requireApplicableRejectsWorkingCopies() {
        for (ConfigStatus s : new ConfigStatus[] {ConfigStatus.DRAFT, ConfigStatus.SCHEDULED}) {
            assertThat(thrown(() -> stored(s).requireApplicable()).code()).isEqualTo(ErrorCode.AI_CONFIG_INVALID_STATE);
        }
    }

    @Test void keepsScheduleOnlyWhenScheduledAndKeepStatus() {
        assertThat(stored(ConfigStatus.SCHEDULED).keepsScheduleOnSave(SaveMode.KEEP_STATUS)).isTrue();
        assertThat(stored(ConfigStatus.SCHEDULED).keepsScheduleOnSave(SaveMode.DRAFT)).isFalse();
        assertThat(stored(ConfigStatus.DRAFT).keepsScheduleOnSave(SaveMode.KEEP_STATUS)).isFalse();
    }

    @Test void editOnDraftUpdatesContentAndStaysDraft() {
        AssistantConfig e = stored(ConfigStatus.DRAFT).edit("n2", "c2", "cn", SaveMode.KEEP_STATUS, "bob", T1);
        assertThat(e.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(e.name()).isEqualTo("n2");
        assertThat(e.content()).isEqualTo("c2");
        assertThat(e.changeNote()).isEqualTo("cn");
        assertThat(e.updatedBy()).isEqualTo("bob");
        assertThat(e.updatedAt()).isEqualTo(T1);
    }

    @Test void editScheduledKeepStatusKeepsSchedule() {
        AssistantConfig e = stored(ConfigStatus.SCHEDULED).edit("n2", "c2", "cn", SaveMode.KEEP_STATUS, "bob", T1);
        assertThat(e.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(e.scheduledAt()).isEqualTo(AT);
    }

    @Test void editScheduledAsDraftClearsSchedule() {
        AssistantConfig e = stored(ConfigStatus.SCHEDULED).edit("n2", "c2", "cn", SaveMode.DRAFT, "bob", T1);
        assertThat(e.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(e.scheduledAt()).isNull();
        assertThat(e.content()).isEqualTo("c2");
    }

    @Test void scheduleSetsStatusAndTime() {
        AssistantConfig s = stored(ConfigStatus.DRAFT).schedule(AT, "bob", T1);
        assertThat(s.status()).isEqualTo(ConfigStatus.SCHEDULED);
        assertThat(s.scheduledAt()).isEqualTo(AT);
        assertThat(s.updatedBy()).isEqualTo("bob");
        assertThat(s.updatedAt()).isEqualTo(T1);
    }

    @Test void publishNowClearsSchedule() {
        AssistantConfig p = stored(ConfigStatus.SCHEDULED).publishNow("bob", T1);
        assertThat(p.status()).isEqualTo(ConfigStatus.PUBLISHED);
        assertThat(p.scheduledAt()).isNull();
        assertThat(p.updatedBy()).isEqualTo("bob");
        assertThat(p.updatedAt()).isEqualTo(T1);
    }

    @Test void copyAsDraftCreatesNewDraftBasedOnThis() {
        AssistantConfig src = stored(ConfigStatus.PUBLISHED);
        AssistantConfig c = src.copyAsDraft(4, "bob", T1);
        assertThat(c.id()).isNull();
        assertThat(c.docVersion()).isNull();
        assertThat(c.status()).isEqualTo(ConfigStatus.DRAFT);
        assertThat(c.version()).isEqualTo(4);
        assertThat(c.basedOnVersion()).isEqualTo(3);
        assertThat(c.configKey()).isEqualTo(INS);
        assertThat(c.configType()).isEqualTo(src.configType());
        assertThat(c.name()).isEqualTo(src.name());
        assertThat(c.content()).isEqualTo(src.content());
        assertThat(c.changeNote()).isNull();
        assertThat(c.createdBy()).isEqualTo("bob");
        assertThat(c.scheduledAt()).isNull();
    }
}
