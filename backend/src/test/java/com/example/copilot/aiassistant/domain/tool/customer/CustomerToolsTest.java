package com.example.copilot.aiassistant.domain.tool.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.application.port.CustomerDataPort;
import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.aiassistant.domain.customer.c360.AlertType;
import com.example.copilot.aiassistant.domain.customer.c360.AssetBlock;
import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.customer.c360.KeyAlert;
import com.example.copilot.aiassistant.domain.customer.c360.KeyAlerts;
import com.example.copilot.aiassistant.domain.customer.c360.LiabilityBlock;
import com.example.copilot.aiassistant.domain.customer.c360.ManagementCard;
import com.example.copilot.aiassistant.domain.customer.c360.MemberRankData;
import com.example.copilot.aiassistant.domain.customer.c360.ProfileCard;
import com.example.copilot.aiassistant.domain.customer.c360.RadarPoint;
import com.example.copilot.aiassistant.domain.customer.c360.RadarView;
import com.example.copilot.aiassistant.domain.customer.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.CitationDetail;
import com.example.copilot.aiassistant.domain.tool.NoArgs;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import com.example.copilot.aiassistant.domain.tool.ToolStatus;
import com.example.copilot.aiassistant.domain.tool.TurnCustomerData;
import com.example.copilot.aiassistant.domain.tool.customer.GetCustomerTool.CustomerArgs;
import com.example.copilot.aiassistant.domain.tool.market.GetCompanyAnnouncementsTool;
import com.example.copilot.aiassistant.domain.tool.market.GetMarketIndexTool;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool;
import com.example.copilot.testsupport.MockCustomerPorts;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CustomerToolsTest {

    private final CustomerDataPort port = MockCustomerPorts.adapter();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private final GetCustomerTool customerTool = new GetCustomerTool(new PiiRedactor());
    private final GetRealTimeCustomerTool realTimeTool = new GetRealTimeCustomerTool(new PiiRedactor());

    private static ToolContext context(CustomerDataPort p, String certNo, AccessLevel level) {
        return new ToolContext(new UserContext("u1", "User", "ROLE", Set.of()),
                new CustomerContext(certNo, level, Set.of(certNo, "王小明"),
                        Set.of("0912-000-111", "xiaoming.wang@example.com", "0123456789012")),
                turnData(p, certNo));
    }

    private ToolContext full(String certNo) {
        return context(port, certNo, AccessLevel.FULL);
    }

    private ToolResult get(ToolContext ctx, String... sections) {
        return customerTool.execute(new CustomerArgs(List.of(sections)), ctx);
    }

    private Map<String, Object> section(String certNo, String key) {
        return map(data(get(full(certNo), key)).get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ToolResult result) {
        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        return (Map<String, Object>) result.data();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strs(Object value) {
        return (List<String>) value;
    }

    private static void assertUnavailable(ToolResult result) {
        assertThat(result.status()).isEqualTo(ToolStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("CUSTOMER_DATA_UNAVAILABLE");
        assertThat(result.message()).isEqualTo("目前無法取得本顧客資料");
    }

    /** The mock port with the 360 record and the real-time status of any customer replaced. */
    private CustomerDataPort portWith(UnaryOperator<Customer360Record> c360, UnaryOperator<RealTimeStatusRecord> rt) {
        return new CustomerDataPort() {
            @Override
            public Optional<CustomerRecord> findCustomer(String certNo) {
                return port.findCustomer(certNo);
            }

            @Override
            public Optional<Customer360Record> findCustomer360(String certNo) {
                return port.findCustomer360(certNo).map(c360);
            }

            @Override
            public Optional<RealTimeStatusRecord> findRealTimeStatus(String certNo) {
                return port.findRealTimeStatus(certNo).map(rt);
            }
        };
    }

    private CustomerDataPort portWith(UnaryOperator<Customer360Record> c360) {
        return portWith(c360, r -> r);
    }

    private static Customer360Record with(Customer360Record r, ManagementCard management, AssetBlock aum,
                                          LiabilityBlock lum, MemberRankData rank) {
        return new Customer360Record(r.dataDate(), r.customerProfile(), management, aum, lum, rank,
                r.wealthPotential(), r.productMix(), r.assetProduct(), r.liabilitiesAndFx(),
                r.accountRelationshipDepth(), r.consumptionBehavior(), r.channelBehavior(), r.digitalBehavior());
    }

    private static UnaryOperator<Customer360Record> withRank(String code, String aum) {
        return r -> with(r, r.customerManagement(), r.aum(), r.lum(),
                new MemberRankData(code, "x", "50", "40", "提醒", aum, "6500000"));
    }

    private Map<String, Object> rank(CustomerDataPort p) {
        return map(data(get(context(p, TestCustomers.RETIREE, AccessLevel.FULL), "memberRank")).get("memberRank"));
    }

    @Test
    void profileFull() {
        ToolResult result = get(full(TestCustomers.RETIREE), "profile");

        Map<String, Object> data = data(result);
        assertThat(data).containsKey("dataDate").containsKey("profile").doesNotContainKey("unavailableSections");
        assertThat(map(data.get("profile"))).containsEntry("subject", "本顧客").containsEntry("ageRange", "55-64")
                .containsEntry("segment", "R01-退休樂活客群").containsEntry("primaryBranch", "B101 信義分行")
                .doesNotContainKeys("name", "age", "financialConsultant", "notes", "profileTags");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).name()).isEqualTo("平台顧客資料（模擬）");
        assertThat(mapper.writeValueAsString(data)).doesNotContain("王小明", "58 歲", "李大發", "16888");
    }

    @Test
    void profileOmitsAgeRangeWhenAgeIsNotNumeric() {
        CustomerDataPort p = portWith(r -> new Customer360Record(r.dataDate(),
                new ProfileCard("x", "n/a", r.customerProfile().segment(), r.customerProfile().primaryBranch(), "y"),
                r.customerManagement(), r.aum(), r.lum(), r.memberRank(), r.wealthPotential(), r.productMix(),
                r.assetProduct(), r.liabilitiesAndFx(), r.accountRelationshipDepth(), r.consumptionBehavior(),
                r.channelBehavior(), r.digitalBehavior()));

        assertThat(map(data(get(context(p, TestCustomers.RETIREE, AccessLevel.FULL), "profile")).get("profile")))
                .doesNotContainKey("ageRange");
    }

    @Test
    void management() {
        Map<String, Object> data = data(get(full(TestCustomers.RETIREE), "management"));
        Map<String, Object> management = map(data.get("management"));

        List<Map<String, Object>> campaigns = list(management.get("campaigns"));
        assertThat(campaigns).hasSize(3);
        assertThat(campaigns).extracting(c -> c.get("digitalDelivery")).containsExactly("否", "是", "否");
        assertThat(campaigns.get(0)).containsKeys("name", "duration", "summary");
        assertThat((List<?>) management.get("recommendedProducts")).hasSize(2);
        assertThat(management).containsKey("insight").doesNotContainKey("keyAlerts");
        assertThat(mapper.writeValueAsString(data)).doesNotContain("receiptNo", "attachment", "CP2026");
    }

    @Test
    void freeTextIsRedactedAndTruncated() {
        String insight = "王小明來電，手機 0912-000-111。" + "字".repeat(300);
        CustomerDataPort p = portWith(r -> with(r, new ManagementCard(List.of(), List.of(), insight), r.aum(),
                r.lum(), r.memberRank()));

        Map<String, Object> management = map(
                data(get(context(p, TestCustomers.RETIREE, AccessLevel.FULL), "management")).get("management"));

        String out = (String) management.get("insight");
        assertThat(out).contains("【本顧客】", "【已遮蔽】").doesNotContain("王小明", "0912-000-111");
        assertThat(out.codePointCount(0, out.length())).isEqualTo(200);
    }

    @Test
    void assetLiability() {
        Map<String, Object> data = section(TestCustomers.RETIREE, "assetLiability");

        Map<String, Object> assets = map(data.get("assets"));
        assertThat(assets).containsEntry("total", "8,200,000 元").containsKeys("segmentTotal", "topTenTotal");
        List<Map<String, Object>> items = list(assets.get("items"));
        assertThat(items).hasSize(6);
        assertThat(items.get(0)).containsEntry("item", "定存").containsEntry("amount", "3,000,000 元")
                .containsEntry("ratio", "36.60%").containsEntry("segmentRatio", "20.00%")
                .containsEntry("vsSegment", "+16.60 個百分點").containsKey("topTenRatio");
        assertThat(list(map(data.get("liabilities")).get("items"))).extracting(i -> i.get("item"))
                .containsExactly("房貸（購置）", "房貸（週轉金）", "副擔保", "信貸", "金融資產融資", "信用卡已使用額度");
        assertThat(data).containsEntry("liabilityToAssetRatio", "0.83");
        Map<String, Object> mix = map(data.get("productMix"));
        assertThat(mix).containsEntry("aumYield", "1.86%").containsKeys("casaRatio", "wealthAumRatio");
        assertThat(map(mix.get("ldr"))).containsEntry("customer", "62.00%").containsEntry("segment", "48.00%")
                .containsEntry("vsSegment", "+14.00 個百分點");
        assertThat(data).doesNotContainKey("dataDate");
    }

    @Test
    void zeroAssetsHasNoRatio() {
        CustomerDataPort p = portWith(r -> with(r, r.customerManagement(),
                new AssetBlock(r.aum().auMData(), 0, 0, 0), r.lum(), r.memberRank()));

        Map<String, Object> data = map(
                data(get(context(p, TestCustomers.RETIREE, AccessLevel.FULL), "assetLiability"))
                        .get("assetLiability"));

        assertThat(data).doesNotContainKey("liabilityToAssetRatio").containsKey("liabilities");
    }

    @Test
    void memberRank() {
        Map<String, Object> wang = section(TestCustomers.RETIREE, "memberRank");
        assertThat(wang).containsEntry("rank", "新富").containsEntry("nextRank", "菁英")
                .containsEntry("gapToNext", "1,800,000 元").containsEntry("downgradeRisk", false)
                .doesNotContainKey("gapToDowngradeLine")
                .containsKeys("progressPercent", "segmentProgressPercent", "alert", "customerAum",
                        "segmentAverageAum", "threshold", "downgradeLine", "nextBenefits");
        assertThat((List<?>) wang.get("currentBenefits")).hasSize(3);

        Map<String, Object> lin = section(TestCustomers.COMPLAINT, "memberRank");
        assertThat(lin).containsEntry("downgradeRisk", true).containsEntry("gapToDowngradeLine", "300,000 元");

        Map<String, Object> huang = section(TestCustomers.NO_MARKETING, "memberRank");
        assertThat(huang).containsEntry("rank", "非理財會員").containsEntry("nextRank", "新富");

        for (Map<String, Object> d : List.of(wang, lin, huang)) {
            assertThat(mapper.writeValueAsString(d)).doesNotContain("memberRankCode", "\"P\"", "\"Q\"");
        }
    }

    @Test
    void memberRankSpecialTiers() {
        Map<String, Object> relationship = rank(portWith(withRank("R", "100")));
        assertThat(relationship.keySet()).containsExactlyInAnyOrder("rank", "note");
        assertThat(relationship).containsEntry("note", "此類顧客不適用等級進度與權益說明");

        Map<String, Object> priv = rank(portWith(withRank("V", "100")));
        assertThat(priv.keySet()).containsExactlyInAnyOrder("rank", "currentBenefits", "alert");

        Map<String, Object> summit = rank(portWith(withRank("M", "50000000")));
        assertThat(summit).doesNotContainKey("gapToNext").containsEntry("rank", "登峰");
    }

    @Test
    void downgradeLineItselfIsNotARisk() {
        Map<String, Object> d = rank(portWith(withRank("P", "3500000")));

        assertThat(d).containsEntry("downgradeRisk", false).doesNotContainKey("gapToDowngradeLine");
    }

    @Test
    void productIntent() {
        Map<String, Object> data = section(TestCustomers.RETIREE, "productIntent");

        Map<String, Object> asset = map(data.get("assetIntent"));
        assertThat(map(asset.get("strongest"))).containsEntry("item", "海外債").containsEntry("index", 81);
        assertThat(map(asset.get("second"))).containsEntry("item", "債券型基金").containsEntry("index", 62);
        assertThat(list(asset.get("items")).get(5)).containsEntry("highlight", true);
        assertThat(map(map(data.get("liabilityFxIntent")).get("strongest"))).containsEntry("item", "外匯・美元");
        Map<String, Object> wealth = map(data.get("wealthPotential"));
        assertThat(strs(wealth.get("aboveSegment"))).first().isEqualTo("不動產估值");
        assertThat(list(wealth.get("items")).get(0)).containsEntry("value", "180 萬");
    }

    @Test
    void behavior() {
        Map<String, Object> data = section(TestCustomers.RETIREE, "behavior");

        assertThat(strs(map(data.get("accountDepth")).get("aboveSegment")))
                .contains("房貸自扣與還款", "公共事業費自扣");
        Map<String, Object> consumption = map(data.get("consumption"));
        assertThat(strs(consumption.get("top3"))).containsExactly("保險投資", "生活繳費", "休閒旅遊");
        assertThat((List<?>) consumption.get("groupTags")).isNotEmpty();
        assertThat(map(map(data.get("channel")).get("mostUsed"))).containsEntry("item", "分行");
        Map<String, Object> digital = map(data.get("digital"));
        assertThat((List<?>) digital.get("belowSegment")).hasSize(4);
        assertThat(list(digital.get("items")).get(0)).containsKey("value");
        assertThat(mapper.writeValueAsString(data.get("channel"))).doesNotContain("回應率");
    }

    @Test
    void onlyRequestedSections() {
        Map<String, Object> data = data(get(full(TestCustomers.RETIREE), "memberRank", "behavior"));

        assertThat(data.keySet()).containsExactly("dataDate", "memberRank", "behavior");
        assertThat(data).containsEntry("dataDate", "2026/10/27");
    }

    @Test
    void duplicateSectionsCollapse() {
        ToolResult result = get(full(TestCustomers.RETIREE), "profile", "profile");

        assertThat(data(result).keySet()).containsExactly("dataDate", "profile");
        assertThat(result.citation().resultCount()).isEqualTo(1);
    }

    @Test
    void unknownSectionIsRejected() {
        ToolResult result = get(full(TestCustomers.RETIREE), "aum");

        assertThat(result.status()).isEqualTo(ToolStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo("TOOL_ARGUMENT_INVALID");
        assertThat(result.message()).isEqualTo("參數格式不正確");
    }

    @Test
    void aSectionThatFailsToRenderIsListedAsUnavailableAndTheOthersStillReturn() {
        CustomerDataPort p = portWith(r -> {
            RadarView w = r.wealthPotential();
            RadarView broken = new RadarView(w.labels(), Arrays.asList((RadarPoint) null), w.segmentData(), w.tagList());
            return new Customer360Record(r.dataDate(), r.customerProfile(), r.customerManagement(), r.aum(), r.lum(),
                    r.memberRank(), broken, r.productMix(), r.assetProduct(), r.liabilitiesAndFx(),
                    r.accountRelationshipDepth(), r.consumptionBehavior(), r.channelBehavior(), r.digitalBehavior());
        });
        ToolContext ctx = context(p, TestCustomers.RETIREE, AccessLevel.FULL);

        ToolResult result = get(ctx, "productIntent", "profile");

        Map<String, Object> data = data(result);
        assertThat(data).containsKey("profile").doesNotContainKey("productIntent")
                .containsEntry("unavailableSections", List.of("productIntent"));
        assertThat(result.citation().resultCount()).isEqualTo(1);
        assertUnavailable(get(ctx, "productIntent"));
    }

    @Test
    void missingSectionIsListed() {
        CustomerDataPort p = portWith(r -> with(r, r.customerManagement(), r.aum(), r.lum(), null));
        ToolContext ctx = context(p, TestCustomers.RETIREE, AccessLevel.FULL);

        ToolResult result = get(ctx, "memberRank", "profile");
        Map<String, Object> data = data(result);
        assertThat(data).containsKey("profile").doesNotContainKey("memberRank")
                .containsEntry("unavailableSections", List.of("memberRank"));
        assertThat(result.citation().title()).isEqualTo("個人360・顧客名片");
        assertThat(result.citation().resultCount()).isEqualTo(1);

        assertUnavailable(get(context(p, TestCustomers.RETIREE, AccessLevel.FULL), "memberRank"));
    }

    @Test
    void missingBlockInsideASectionIsOmitted() {
        CustomerDataPort p = portWith(r -> with(r, r.customerManagement(), r.aum(), null, r.memberRank()));

        Map<String, Object> data = map(
                data(get(context(p, TestCustomers.RETIREE, AccessLevel.FULL), "assetLiability"))
                        .get("assetLiability"));

        assertThat(data).containsKey("assets").doesNotContainKeys("liabilities", "liabilityToAssetRatio");
    }

    @Test
    void limitedGetsProfileOnly() {
        AtomicInteger realTimeCalls = new AtomicInteger();
        CustomerDataPort counting = new CustomerDataPort() {
            @Override
            public Optional<CustomerRecord> findCustomer(String certNo) {
                return port.findCustomer(certNo);
            }

            @Override
            public Optional<Customer360Record> findCustomer360(String certNo) {
                return port.findCustomer360(certNo);
            }

            @Override
            public Optional<RealTimeStatusRecord> findRealTimeStatus(String certNo) {
                realTimeCalls.incrementAndGet();
                return port.findRealTimeStatus(certNo);
            }
        };

        ToolResult result = get(context(counting, TestCustomers.HIGH_ASSET, AccessLevel.LIMITED),
                "profile", "assetLiability");

        Map<String, Object> data = data(result);
        assertThat(data.keySet()).containsExactlyInAnyOrder("dataDate", "profile", "accessNote",
                "unavailableSections");
        assertThat(map(data.get("profile")).keySet()).containsExactlyInAnyOrder("subject", "ageRange", "segment");
        assertThat(data).containsEntry("accessNote", "依您的權限僅能查看去識別化基本輪廓")
                .containsEntry("unavailableSections", List.of("assetLiability"));
        assertThat(mapper.writeValueAsString(data)).doesNotContain("高資產", "primaryBranch");
        assertThat(realTimeCalls).hasValue(0);
        assertThat(result.citation().title()).isEqualTo("個人360・顧客名片");
        assertThat(result.citation().resultCount()).isEqualTo(1);
    }

    @Test
    void limitedProfileOnlyHasNoUnavailableList() {
        Map<String, Object> data = data(get(context(port, TestCustomers.HIGH_ASSET, AccessLevel.LIMITED), "profile"));

        assertThat(data).doesNotContainKey("unavailableSections");
    }

    @Test
    void realTimeCustomer() {
        Map<String, Object> huang = data(realTimeTool.execute(new NoArgs(), full(TestCustomers.NO_MARKETING)));
        assertThat(list(huang.get("notes"))).contains(Map.of("category", "不宜行銷", "expiryDate", "2027/03/31"));
        assertThat(list(data(realTimeTool.execute(new NoArgs(), full(TestCustomers.ATTENTION))).get("notes")))
                .extracting(n -> n.get("category")).contains("應注意名單");
        assertThat(list(data(realTimeTool.execute(new NoArgs(), full(TestCustomers.SPECIAL_CARE))).get("notes")))
                .extracting(n -> n.get("category")).contains("特殊客群");

        Map<String, Object> wang = data(realTimeTool.execute(new NoArgs(), full(TestCustomers.RETIREE)));
        assertThat(wang).containsEntry("notes", List.of()).doesNotContainKey("dataDate");
        List<Map<String, Object>> alerts = list(wang.get("keyAlerts"));
        assertThat(alerts).hasSize(3);
        assertThat(alerts.get(0)).containsEntry("category", "資金活水").containsKeys("title", "detail", "date");
        assertThat(strs(wang.get("profileTags"))).hasSize(3);
        assertThat(mapper.writeValueAsString(wang)).doesNotContain("王小明", "58 歲", "李大發", "16888");

        assertThat(realTimeTool.requiredAccessLevel()).isEqualTo(AccessLevel.FULL);
    }

    @Test
    void realTimeWithoutAlertsGivesAnEmptyList() {
        CustomerDataPort p = portWith(r -> r, rt -> new RealTimeStatusRecord(false, false, null, List.of(), null));

        Map<String, Object> data = data(realTimeTool.execute(new NoArgs(),
                context(p, TestCustomers.RETIREE, AccessLevel.FULL)));

        assertThat(data).containsEntry("keyAlerts", List.of()).containsEntry("notes", List.of())
                .doesNotContainKey("profileTags");
    }

    @Test
    void realTimeAlertTextIsRedacted() {
        String detail = "王小明來電，手機 0912-000-111。";
        CustomerDataPort p = portWith(r -> r, rt -> new RealTimeStatusRecord(rt.attentionCustomer(),
                rt.specialCareCustomer(), rt.profilePhoto(), rt.customerFlaggingList(),
                new KeyAlerts(List.of(
                        new KeyAlert(
                                AlertType.OTHER, "標題", detail,
                                "2026/10/27")))));

        Map<String, Object> data = data(realTimeTool.execute(new NoArgs(),
                context(p, TestCustomers.RETIREE, AccessLevel.FULL)));

        assertThat(mapper.writeValueAsString(data)).doesNotContain("王小明", "0912-000-111").contains("【已遮蔽】");
    }

    @Test
    void missingCustomerDataYieldsUnavailable() {
        ToolContext ctx = full(TestCustomers.UNKNOWN);

        assertUnavailable(get(ctx, "profile"));
        assertUnavailable(realTimeTool.execute(new NoArgs(), ctx));
    }

    @Test
    void contextWithoutDataYieldsUnavailable() {
        ToolContext ctx = new ToolContext(new UserContext("u1", "User", "ROLE", Set.of()),
                new CustomerContext(TestCustomers.RETIREE, AccessLevel.FULL, Set.of(), Set.of()));

        assertUnavailable(get(ctx, "profile"));
        assertUnavailable(realTimeTool.execute(new NoArgs(), ctx));
    }

    @Test
    void accessLevels() {
        assertThat(customerTool.requiredAccessLevel()).isEqualTo(AccessLevel.LIMITED);
        assertThat(realTimeTool.requiredAccessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(customerTool.name()).isEqualTo("getCustomer");
        assertThat(realTimeTool.name()).isEqualTo("getRealTimeCustomer");
        assertThat(customerTool.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(realTimeTool.timeout()).isEqualTo(Duration.ofSeconds(3));

        List<AssistantTool<?>> all = new ArrayList<>(List.of(customerTool, realTimeTool));
        all.add(new GetMarketIndexTool(null));
        all.add(new GetStockQuoteTool(null));
        all.add(new GetCompanyAnnouncementsTool(null));
        ToolRegistry registry = new ToolRegistry(all);
        assertThat(registry.toolsFor(AccessLevel.LIMITED)).extracting(AssistantTool::name)
                .containsExactlyInAnyOrder("getCustomer", "getMarketIndex", "getStockQuote",
                        "getCompanyAnnouncements");
        assertThat(registry.toolsFor(AccessLevel.FULL)).extracting(AssistantTool::name)
                .containsExactlyInAnyOrder("getCustomer", "getRealTimeCustomer", "getMarketIndex", "getStockQuote",
                        "getCompanyAnnouncements");
    }

    @Test
    void specificationListsEverySection() {
        var parameters = customerTool.specification().parameters();

        assertThat(parameters.properties()).containsOnlyKeys("sections");
        assertThat(parameters.required()).containsExactly("sections");
        var sections = (JsonArraySchema) parameters.properties().get("sections");
        assertThat(((JsonEnumSchema) sections.items()).enumValues())
                .containsExactly("profile", "management", "assetLiability", "memberRank", "productIntent",
                        "behavior");
        for (CustomerSection s : CustomerSection.values()) {
            assertThat(s.description()).startsWith(s.key() + "（" + s.displayName() + "）：");
            assertThat(sections.description()).contains(s.description());
        }
    }

    @Test
    void citations() {
        ToolResult wang = get(full(TestCustomers.RETIREE), "memberRank", "assetLiability");
        CitationDetail citation = wang.citation();
        assertThat(citation.title()).isEqualTo("個人360・資產負債、會員等級");
        assertThat(citation.description()).isEqualTo("資料日期 2026/10/27");
        assertThat(citation.resultCount()).isEqualTo(2);
        assertThat(citation.excerpt()).isNull();
        assertThat(citation.target()).isNull();

        assertThat(get(full(TestCustomers.RETIREE), "profile", "management", "assetLiability", "memberRank",
                "productIntent", "behavior").citation().title())
                .isEqualTo("個人360・顧客名片、顧客經營、資產負債、會員等級、產品意圖、往來與行為");

        ToolResult limited = get(context(port, TestCustomers.RETIREE, AccessLevel.LIMITED), "profile");
        assertThat(limited.citation().title()).isEqualTo("個人360・顧客名片");

        CitationDetail realTime = realTimeTool.execute(new NoArgs(), full(TestCustomers.RETIREE)).citation();
        assertThat(realTime.title()).isEqualTo("個人360・即時註記與提醒");
        assertThat(realTime.description()).isEqualTo("註記、畫像標籤、關鍵提醒");
        assertThat(realTime.resultCount()).isEqualTo(3);
        assertThat(realTime.excerpt()).isNull();
    }

    @Test
    void purposes() {
        assertThat(customerTool.purpose()).isEqualTo("查詢顧客 360 資料");
        assertThat(realTimeTool.purpose()).isEqualTo("查詢顧客即時註記與提醒");
    }

    private static TurnCustomerData turnData(CustomerDataPort port, String certNo) {
        return new TurnCustomerData(() -> port.findCustomer360(certNo), () -> port.findRealTimeStatus(certNo));
    }
}
