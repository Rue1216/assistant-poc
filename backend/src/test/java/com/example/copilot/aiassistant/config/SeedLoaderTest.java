package com.example.copilot.aiassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

class SeedLoaderTest extends IntegrationTestBase {

    @Autowired AssistantConfigRepository repository;
    @Autowired SeedLoader seedLoader;
    @Autowired ConfigValidator validator;

    private static final List<String> MANIFEST_ORDER = List.of("instruction.customer-overview",
            "instruction.asset-liability", "instruction.member-rank", "instruction.opportunity",
            "instruction.engagement", "instruction.contact-prep", "instruction.market-brief");

    @BeforeEach
    void cleanAndSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    @AfterEach
    void restoreSeed() {
        repository.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    @Test
    void seedsEightPublishedConfigs() {
        List<AssistantConfig> published = repository.findByStatus(ConfigStatus.PUBLISHED);
        assertThat(published).hasSize(8);
        assertThat(published).allSatisfy(c -> {
            assertThat(c.getVersion()).isEqualTo(1);
            assertThat(c.getUpdatedBy()).isEqualTo("system");
            assertThat(c.getCreatedBy()).isEqualTo("system");
            assertThat(c.getContent()).isNotBlank();
        });
        assertThat(published).extracting(AssistantConfig::getConfigKey).containsExactlyInAnyOrder(
                "system.main", "instruction.customer-overview", "instruction.asset-liability",
                "instruction.member-rank", "instruction.opportunity", "instruction.engagement",
                "instruction.contact-prep", "instruction.market-brief");
        assertThat(repository.findByConfigKeyAndStatus("instruction.contact-topic", ConfigStatus.PUBLISHED))
                .isEmpty();
    }

    @Test
    void sevenCategoriesInManifestOrderWithNames() {
        List<AssistantConfig> instructions = repository.findByStatus(ConfigStatus.PUBLISHED).stream()
                .filter(c -> c.getConfigType() == ConfigType.SYSTEM_INSTRUCTION)
                .sorted(Comparator.comparing(AssistantConfig::getId))
                .toList();
        assertThat(instructions).extracting(AssistantConfig::getConfigKey).containsExactlyElementsOf(MANIFEST_ORDER);
        assertThat(instructions).extracting(c -> c.getConfigKey() + "=" + c.getName()).containsExactly(
                "instruction.customer-overview=顧客概況", "instruction.asset-liability=資產負債",
                "instruction.member-rank=會員等級", "instruction.opportunity=商品機會",
                "instruction.engagement=往來與通路", "instruction.contact-prep=聯繫準備",
                "instruction.market-brief=市場快訊");
    }

    @Test
    void everySeedPassesTheValidator() {
        assertThat(repository.findByStatus(ConfigStatus.PUBLISHED)).allSatisfy(c ->
                assertThat(validator.validate(c.getConfigType(), c.getConfigKey(), c.getContent()))
                        .as(c.getConfigKey()).isEmpty());
    }

    @Test
    void systemMainMentionsNotesInvestmentAndCustomerTools() {
        String text = content("system.main");
        assertThat(text).contains("## 顧客註記", "## 商品與投資", "getCustomer", "getRealTimeCustomer");
        assertThat(java.util.regex.Pattern.compile("get[A-Z]\\w+").matcher(text).results().map(r -> r.group()))
                .containsOnly("getCustomer", "getRealTimeCustomer");
    }

    @Test
    void marketBriefUsesSlashDates() {
        assertThat(content("instruction.market-brief")).doesNotContain("2026-10-16");
        assertThat(content("system.main")).contains("2026/10/19").doesNotContain("2026-10-16");
    }

    private String content(String key) {
        return repository.findByConfigKeyAndStatus(key, ConfigStatus.PUBLISHED).orElseThrow().getContent();
    }

    @Test
    void seedIsIdempotent() {
        seedLoader.run(new DefaultApplicationArguments());
        assertThat(repository.findByStatus(ConfigStatus.PUBLISHED)).hasSize(8);
        assertThat(repository.count()).isEqualTo(8);
    }
}
