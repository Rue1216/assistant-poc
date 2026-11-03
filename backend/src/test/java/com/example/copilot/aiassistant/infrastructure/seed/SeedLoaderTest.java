package com.example.copilot.aiassistant.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.repository.AssistantConfigRepository;
import com.example.copilot.aiassistant.infrastructure.mongo.AssistantConfigMongoRepository;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

class SeedLoaderTest extends IntegrationTestBase {

    @Autowired AssistantConfigRepository repository;
    @Autowired AssistantConfigMongoRepository documents;
    @Autowired SeedLoader seedLoader;
    @Autowired ConfigValidator validator;

    private static final List<String> MANIFEST_ORDER = List.of("instruction.customer-overview",
            "instruction.asset-liability", "instruction.member-rank", "instruction.opportunity",
            "instruction.engagement", "instruction.contact-prep", "instruction.market-brief");

    @BeforeEach
    void cleanAndSeed() {
        documents.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    @AfterEach
    void restoreSeed() {
        documents.deleteAll();
        seedLoader.run(new DefaultApplicationArguments());
    }

    @Test
    void seedsEightPublishedConfigs() {
        List<AssistantConfig> published = repository.findPublished();
        assertThat(published).hasSize(8);
        assertThat(published).allSatisfy(c -> {
            assertThat(c.version()).isEqualTo(1);
            assertThat(c.updatedBy()).isEqualTo("system");
            assertThat(c.createdBy()).isEqualTo("system");
            assertThat(c.content()).isNotBlank();
        });
        assertThat(published).extracting(AssistantConfig::configKey).containsExactlyInAnyOrder(
                "system.main", "instruction.customer-overview", "instruction.asset-liability",
                "instruction.member-rank", "instruction.opportunity", "instruction.engagement",
                "instruction.contact-prep", "instruction.market-brief");
        assertThat(repository.findPublished("instruction.contact-topic"))
                .isEmpty();
    }

    @Test
    void sevenCategoriesInManifestOrderWithNames() {
        List<AssistantConfig> instructions = repository.findPublished().stream()
                .filter(c -> c.configType() == ConfigType.SYSTEM_INSTRUCTION)
                .sorted(Comparator.comparing(AssistantConfig::id))
                .toList();
        assertThat(instructions).extracting(AssistantConfig::configKey).containsExactlyElementsOf(MANIFEST_ORDER);
        assertThat(instructions).extracting(c -> c.configKey() + "=" + c.name()).containsExactly(
                "instruction.customer-overview=顧客概況", "instruction.asset-liability=資產負債",
                "instruction.member-rank=會員等級", "instruction.opportunity=商品機會",
                "instruction.engagement=往來與通路", "instruction.contact-prep=聯繫準備",
                "instruction.market-brief=市場快訊");
    }

    @Test
    void everySeedPassesTheValidator() {
        assertThat(repository.findPublished()).allSatisfy(c ->
                assertThat(validator.validate(c.configType(), c.configKey(), c.content()))
                        .as(c.configKey()).isEmpty());
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
        return repository.findPublished(key).orElseThrow().content();
    }

    @Test
    void seedIsIdempotent() {
        seedLoader.run(new DefaultApplicationArguments());
        assertThat(repository.findPublished()).hasSize(8);
        assertThat(repository.count()).isEqualTo(8);
    }
}
