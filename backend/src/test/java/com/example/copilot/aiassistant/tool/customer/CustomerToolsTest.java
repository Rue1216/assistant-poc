package com.example.copilot.aiassistant.tool.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.InteractionRecord;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolExecutor;
import com.example.copilot.aiassistant.tool.ToolGuard;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import com.example.copilot.mockplatform.customer.MockCustomerData;
import com.example.copilot.mockplatform.customer.MockCustomerDataAdapter;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CustomerToolsTest {

    private final CustomerDataPort port = new MockCustomerDataAdapter(new MockCustomerData());
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneOffset.UTC);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private final GetCustomerProfileTool profileTool = new GetCustomerProfileTool(port, clock);
    private final GetCustomerHoldingsTool holdingsTool = new GetCustomerHoldingsTool(port);
    private final GetRecentInteractionsTool interactionsTool = new GetRecentInteractionsTool(port, new PiiRedactor());
    private final GetCampaignResultsTool campaignsTool = new GetCampaignResultsTool(port);

    private static ToolContext context(String customerId, AccessLevel level) {
        return new ToolContext(new UserContext("u1", "User", "ROLE", Set.of()),
                new CustomerContext(customerId, level, Set.of(customerId, "王小明"),
                        Set.of("A123456789", "0912-000-111", "xiaoming.wang@example.com", "0123456789012")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ToolResult result) {
        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        return (Map<String, Object>) result.data();
    }

    private static List<String> keys(Map<?, ?> map) {
        return map.keySet().stream().map(String::valueOf).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullProfileMatchesSpec() {
        ToolResult result = profileTool.execute(new NoArgs(), context("C001", AccessLevel.FULL));

        Map<String, Object> data = data(result);
        assertThat(data.keySet()).containsExactly("subject", "segment", "ageRange", "relationshipYears",
                "assetTier", "riskProfile", "marketingConsent", "preferredChannel", "tags");
        assertThat(data).containsEntry("subject", "本顧客").containsEntry("segment", "VIP")
                .containsEntry("ageRange", "55-64").containsEntry("relationshipYears", 8)
                .containsEntry("assetTier", "500萬-1000萬").containsEntry("riskProfile", "穩健型")
                .containsEntry("marketingConsent", true).containsEntry("preferredChannel", "電話");
        assertThat((List<Object>) data.get("tags")).containsExactly("退休規劃", "偏好定期定額", "高股息偏好");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).name()).isEqualTo("平台顧客資料（模擬）");
    }

    @Test
    void limitedProfileHasOnlyBasicFields() {
        Map<String, Object> data = data(profileTool.execute(new NoArgs(), context("C001", AccessLevel.LIMITED)));

        assertThat(data.keySet()).containsExactly("subject", "segment", "ageRange", "relationshipYears",
                "accessNote");
        assertThat(data).containsEntry("accessNote", "依您的權限僅能查看去識別化基本輪廓");
    }

    @Test
    void holdingsUseWeightRanges() {
        Map<String, Object> data = data(holdingsTool.execute(new NoArgs(), context("C001", AccessLevel.FULL)));

        List<?> holdings = (List<?>) data.get("holdings");
        assertThat(holdings).hasSize(3);
        Map<?, ?> first = (Map<?, ?>) holdings.get(0);
        assertThat(keys(first)).containsExactly("productType", "productName", "stockCode", "weightRange");
        assertThat(first.get("productName")).isEqualTo("台積電");
        assertThat(first.get("stockCode")).isEqualTo("2330");
        assertThat(first.get("weightRange")).isEqualTo("20%-30%");
        assertThat(holdings.toString()).doesNotContain("0.25");
    }

    @Test
    void interactionsDropHandledBy() {
        Map<String, Object> data = data(interactionsTool.execute(
                new GetRecentInteractionsTool.InteractionArgs(null), context("C001", AccessLevel.FULL)));

        List<?> rows = (List<?>) data.get("interactions");
        assertThat(rows).hasSize(2);
        for (Object row : rows) {
            assertThat(keys((Map<?, ?>) row)).containsExactly("date", "channel", "topic", "summary",
                    "outcome");
        }
        assertThat(((Map<?, ?>) rows.get(0)).get("date")).isEqualTo("2026-10-02");
    }

    @Test
    void interactionsHonourLimit() {
        Map<String, Object> data = data(interactionsTool.execute(
                new GetRecentInteractionsTool.InteractionArgs(1), context("C001", AccessLevel.FULL)));
        assertThat((List<?>) data.get("interactions")).hasSize(1);
    }

    @Test
    void campaignsReturnSpecFields() {
        Map<String, Object> data = data(campaignsTool.execute(new NoArgs(), context("C001", AccessLevel.FULL)));

        List<?> rows = (List<?>) data.get("campaigns");
        assertThat(rows).hasSize(2);
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        assertThat(keys(first)).containsExactly("campaignName", "date", "channel", "response");
        assertThat(first.get("response")).isEqualTo("CLICKED");
    }

    @Test
    void missingCustomerYieldsUnavailable() {
        ToolContext ctx = context("C999", AccessLevel.FULL);
        List<ToolResult> results = List.of(
                profileTool.execute(new NoArgs(), ctx),
                holdingsTool.execute(new NoArgs(), ctx),
                interactionsTool.execute(new GetRecentInteractionsTool.InteractionArgs(null), ctx),
                campaignsTool.execute(new NoArgs(), ctx));

        for (ToolResult result : results) {
            assertThat(result.status()).isEqualTo(ToolStatus.ERROR);
            assertThat(result.errorCode()).isEqualTo("CUSTOMER_DATA_UNAVAILABLE");
            assertThat(result.message()).isEqualTo("目前無法取得本顧客資料");
        }
    }

    @Test
    void requiredLevelsAndNames() {
        assertThat(profileTool.requiredAccessLevel()).isEqualTo(AccessLevel.LIMITED);
        assertThat(holdingsTool.requiredAccessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(interactionsTool.requiredAccessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(campaignsTool.requiredAccessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(List.of(profileTool.name(), holdingsTool.name(), interactionsTool.name(), campaignsTool.name()))
                .containsExactly("getCustomerProfile", "getCustomerHoldings", "getRecentInteractions",
                        "getCampaignResults");
        assertThat(Optional.of(profileTool.timeout())).contains(Duration.ofSeconds(3));
    }

    @Test
    void identifierStraddlingTheSummaryCutIsRedactedBeforeTruncation() {
        String summary = "字".repeat(95) + "0912-000-111 後續聯絡";
        CustomerDataPort stub = new MockCustomerDataAdapter(new MockCustomerData()) {
            @Override
            public List<InteractionRecord> findRecentInteractions(String customerId, int limit) {
                return List.of(new InteractionRecord(LocalDate.of(2026, 10, 2), "電話", "t", summary, "o", "h"));
            }
        };
        GetRecentInteractionsTool tool = new GetRecentInteractionsTool(stub, new PiiRedactor());
        ToolContext ctx = context("C001", AccessLevel.FULL);

        ToolResult result = tool.execute(new GetRecentInteractionsTool.InteractionArgs(1), ctx);

        String text = data(result).toString();
        assertThat(text).doesNotContain("0912", "2-000", "000-1", "-111", "0912000111");
        assertThat(text).contains("【已遮蔽】");
    }

    @Test
    void profileAgeUsesTaipeiDate() {
        // 2026-10-19T17:00Z is already 2026-10-20 in Taipei; the customer turns 65 that day.
        Clock late = Clock.fixed(Instant.parse("2026-10-19T17:00:00Z"), ZoneOffset.UTC);
        GetCustomerProfileTool tool = new GetCustomerProfileTool(new MockCustomerDataAdapter(new MockCustomerData()) {
            @Override
            public Optional<CustomerRecord> findCustomer(String customerId) {
                return super.findCustomer(customerId).map(r -> new CustomerRecord(r.customerId(), r.name(),
                        r.nationalId(), r.mobile(), r.email(), r.accountNumbers(), LocalDate.of(1961, 10, 20),
                        r.employer(), r.address(), r.segment(), r.relationshipYears(), r.assetAmount(),
                        r.riskProfile(), r.marketingConsent(), r.preferredChannel(), r.tags(), r.primaryBranch(),
                        r.profitBranch(), r.wealthBranch(), r.assigneeId(), r.assigneeBranch(),
                        r.marketingUnit()));
            }
        }, late);

        Map<String, Object> data = data(tool.execute(new NoArgs(), context("C001", AccessLevel.FULL)));

        assertThat(data).containsEntry("ageRange", "65+");
    }

    @Test
    void serializedOutputContainsNoIdentifiers() {
        AiAssistantProperties props = new AiAssistantProperties(null,
                new AiAssistantProperties.Limits(1000, 10, 8000, 5, 3, Duration.ofSeconds(60), 16000,
                        Duration.ofSeconds(5), 4096, 10), null, null);
        ToolGuard guard = new ToolGuard(
                new ToolRegistry(List.of(profileTool, holdingsTool, interactionsTool, campaignsTool)),
                Validation.buildDefaultValidatorFactory().getValidator(), mapper);
        ToolExecutor executor = new ToolExecutor(guard, new PiiRedactor(), mapper, props);
        ToolContext ctx = context("C001", AccessLevel.FULL);
        Set<String> offered = Set.of("getCustomerProfile", "getCustomerHoldings", "getRecentInteractions",
                "getCampaignResults");

        for (String tool : offered) {
            ToolOutcome outcome = executor.execute(
                    ToolExecutionRequest.builder().id("1").name(tool).arguments("{}").build(), ctx, offered);
            assertThat(outcome.result().status()).as(tool).isEqualTo(ToolStatus.SUCCESS);
            if (tool.equals("getRecentInteractions")) {
                assertThat(outcome.payloadForModel()).contains("【本顧客】詢問月配息商品");
            }
            assertThat(outcome.payloadForModel()).as(tool)
                    .doesNotContain("王小明", "C001", "A123456789", "0912-000-111", "0912000111",
                            "xiaoming.wang@example.com", "0123456789012", "1968-03-14", "忠孝西路",
                            "宏達電子");
        }
    }
}
