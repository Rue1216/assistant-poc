package com.example.copilot.aiassistant.tool.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.port.c360.AssetBlock;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.LiabilityBlock;
import com.example.copilot.aiassistant.port.c360.ManagementCard;
import com.example.copilot.aiassistant.port.c360.MemberRankData;
import com.example.copilot.aiassistant.port.c360.ProfileCard;
import com.example.copilot.aiassistant.port.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.tool.AssistantTool;
import com.example.copilot.aiassistant.tool.CitationDetail;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import com.example.copilot.aiassistant.tool.market.GetCompanyAnnouncementsTool;
import com.example.copilot.aiassistant.tool.market.GetMarketIndexTool;
import com.example.copilot.aiassistant.tool.market.GetStockQuoteTool;
import com.example.copilot.testsupport.MockCustomerPorts;
import java.time.Duration;
import java.util.ArrayList;
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

    private final GetCustomerProfileTool profileTool = new GetCustomerProfileTool(port);

    private static ToolContext context(String certNo, AccessLevel level) {
        return new ToolContext(new UserContext("u1", "User", "ROLE", Set.of()),
                new CustomerContext(certNo, level, Set.of(certNo, "王小明"),
                        Set.of("0912-000-111", "xiaoming.wang@example.com", "0123456789012")));
    }

    private static ToolContext full(String certNo) {
        return context(certNo, AccessLevel.FULL);
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

    @Test
    void profileFull() {
        ToolResult result = profileTool.execute(new NoArgs(), full(TestCustomers.RETIREE));

        Map<String, Object> data = data(result);
        assertThat(data).containsEntry("subject", "本顧客").containsEntry("ageRange", "55-64")
                .containsEntry("segment", "R01-退休樂活客群").containsEntry("primaryBranch", "B101 信義分行")
                .containsEntry("notes", List.of()).containsKey("profileTags").containsKey("dataDate")
                .doesNotContainKeys("name", "age", "financialConsultant");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).name()).isEqualTo("平台顧客資料（模擬）");
        assertThat(mapper.writeValueAsString(data)).doesNotContain("王小明", "58 歲", "李大發", "16888");

        assertThat(list(data(profileTool.execute(new NoArgs(), full(TestCustomers.NO_MARKETING))).get("notes")))
                .contains(Map.of("category", "不宜行銷", "expiryDate", "2027/03/31"));
        assertThat(list(data(profileTool.execute(new NoArgs(), full(TestCustomers.ATTENTION))).get("notes")))
                .extracting(n -> n.get("category")).contains("應注意名單");
        assertThat(list(data(profileTool.execute(new NoArgs(), full(TestCustomers.SPECIAL_CARE))).get("notes")))
                .extracting(n -> n.get("category")).contains("特殊客群");
    }

    @Test
    void profileLimited() {
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

        ToolResult result = new GetCustomerProfileTool(counting)
                .execute(new NoArgs(), context(TestCustomers.HIGH_ASSET, AccessLevel.LIMITED));

        Map<String, Object> data = data(result);
        assertThat(data.keySet()).containsExactlyInAnyOrder("subject", "ageRange", "segment", "accessNote");
        assertThat(data).containsEntry("accessNote", "依您的權限僅能查看去識別化基本輪廓");
        assertThat(mapper.writeValueAsString(data)).doesNotContain("高資產");
        assertThat(realTimeCalls).hasValue(0);
    }

    @Test
    void profileOmitsAgeRangeWhenAgeIsNotNumeric() {
        CustomerDataPort p = portWith(r -> new Customer360Record(r.dataDate(),
                new ProfileCard("x", "n/a", r.customerProfile().segment(), r.customerProfile().primaryBranch(), "y"),
                r.customerManagement(), r.aum(), r.lum(), r.memberRank(), r.wealthPotential(), r.productMix(),
                r.assetProduct(), r.liabilitiesAndFx(), r.accountRelationshipDepth(), r.consumptionBehavior(),
                r.channelBehavior(), r.digitalBehavior()));

        assertThat(data(new GetCustomerProfileTool(p).execute(new NoArgs(), full(TestCustomers.RETIREE))))
                .doesNotContainKey("ageRange");
    }

    @Test
    void managementSummary() {
        Map<String, Object> data = data(new GetManagementSummaryTool(port, new PiiRedactor())
                .execute(new NoArgs(), full(TestCustomers.RETIREE)));

        List<Map<String, Object>> alerts = list(data.get("keyAlerts"));
        assertThat(alerts).hasSize(3);
        assertThat(alerts.get(0)).containsEntry("category", "資金活水").containsKeys("title", "detail", "date");
        List<Map<String, Object>> campaigns = list(data.get("campaigns"));
        assertThat(campaigns).hasSize(3);
        assertThat(campaigns).extracting(c -> c.get("digitalDelivery")).containsExactly("否", "是", "否");
        assertThat(campaigns.get(0)).containsKeys("name", "duration", "summary");
        assertThat((List<?>) data.get("recommendedProducts")).hasSize(2);
        assertThat(data).containsKeys("insight", "dataDate");
        assertThat(mapper.writeValueAsString(data)).doesNotContain("receiptNo", "attachment", "CP2026");
    }

    @Test
    void freeTextIsRedactedAndTruncated() {
        String insight = "王小明來電，手機 0912-000-111。" + "字".repeat(300);
        CustomerDataPort p = portWith(r -> with(r, new ManagementCard(List.of(), List.of(), insight), r.aum(),
                r.lum(), r.memberRank()));

        Map<String, Object> data = data(new GetManagementSummaryTool(p, new PiiRedactor())
                .execute(new NoArgs(), full(TestCustomers.RETIREE)));

        String out = (String) data.get("insight");
        assertThat(out).contains("【本顧客】", "【已遮蔽】").doesNotContain("王小明", "0912-000-111");
        assertThat(out.codePointCount(0, out.length())).isEqualTo(200);
    }

    @Test
    void assetLiability() {
        Map<String, Object> data = data(
                new GetAssetLiabilityTool(port).execute(new NoArgs(), full(TestCustomers.RETIREE)));

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
    }

    @Test
    void zeroAssetsHasNoRatio() {
        CustomerDataPort p = portWith(r -> with(r, r.customerManagement(),
                new AssetBlock(r.aum().auMData(), 0, 0, 0), r.lum(), r.memberRank()));

        Map<String, Object> data = data(
                new GetAssetLiabilityTool(p).execute(new NoArgs(), full(TestCustomers.RETIREE)));

        assertThat(data).doesNotContainKey("liabilityToAssetRatio").containsKey("liabilities");
    }

    @Test
    void memberRank() {
        GetMemberRankTool tool = new GetMemberRankTool(port);

        Map<String, Object> wang = data(tool.execute(new NoArgs(), full(TestCustomers.RETIREE)));
        assertThat(wang).containsEntry("rank", "新富").containsEntry("nextRank", "菁英")
                .containsEntry("gapToNext", "1,800,000 元").containsEntry("downgradeRisk", false)
                .doesNotContainKey("gapToDowngradeLine")
                .containsKeys("progressPercent", "segmentProgressPercent", "alert", "customerAum",
                        "segmentAverageAum", "threshold", "downgradeLine", "nextBenefits", "dataDate");
        assertThat((List<?>) wang.get("currentBenefits")).hasSize(3);

        Map<String, Object> lin = data(tool.execute(new NoArgs(), full(TestCustomers.COMPLAINT)));
        assertThat(lin).containsEntry("downgradeRisk", true).containsEntry("gapToDowngradeLine", "300,000 元");

        Map<String, Object> huang = data(tool.execute(new NoArgs(), full(TestCustomers.NO_MARKETING)));
        assertThat(huang).containsEntry("rank", "非理財會員").containsEntry("nextRank", "新富");

        for (Map<String, Object> d : List.of(wang, lin, huang)) {
            assertThat(mapper.writeValueAsString(d)).doesNotContain("memberRankCode", "\"P\"", "\"Q\"");
        }
    }

    @Test
    void memberRankSpecialTiers() {
        ToolContext ctx = full(TestCustomers.RETIREE);

        Map<String, Object> relationship = data(new GetMemberRankTool(portWith(withRank("R", "100")))
                .execute(new NoArgs(), ctx));
        assertThat(relationship.keySet()).containsExactlyInAnyOrder("rank", "note", "dataDate");
        assertThat(relationship).containsEntry("note", "此類顧客不適用等級進度與權益說明");

        Map<String, Object> priv = data(new GetMemberRankTool(portWith(withRank("V", "100")))
                .execute(new NoArgs(), ctx));
        assertThat(priv.keySet()).containsExactlyInAnyOrder("rank", "currentBenefits", "alert", "dataDate");

        Map<String, Object> summit = data(new GetMemberRankTool(portWith(withRank("M", "50000000")))
                .execute(new NoArgs(), ctx));
        assertThat(summit).doesNotContainKey("gapToNext").containsEntry("rank", "登峰");
    }

    @Test
    void downgradeLineItselfIsNotARisk() {
        Map<String, Object> d = data(new GetMemberRankTool(portWith(withRank("P", "3500000")))
                .execute(new NoArgs(), full(TestCustomers.RETIREE)));

        assertThat(d).containsEntry("downgradeRisk", false).doesNotContainKey("gapToDowngradeLine");
    }

    @Test
    void productIntent() {
        Map<String, Object> data = data(
                new GetProductIntentTool(port).execute(new NoArgs(), full(TestCustomers.RETIREE)));

        Map<String, Object> asset = map(data.get("assetIntent"));
        assertThat(map(asset.get("strongest"))).containsEntry("item", "海外債").containsEntry("index", 81);
        assertThat(map(asset.get("second"))).containsEntry("item", "債券型基金").containsEntry("index", 62);
        assertThat(list(asset.get("items")).get(5)).containsEntry("highlight", true);
        assertThat(map(map(data.get("liabilityFxIntent")).get("strongest"))).containsEntry("item", "外匯・美元");
        Map<String, Object> wealth = map(data.get("wealthPotential"));
        assertThat(strs(wealth.get("aboveSegment"))).first().isEqualTo("不動產估值");
        assertThat(list(wealth.get("items")).get(0)).containsEntry("value", "180 萬");
        assertThat(data).containsKey("dataDate");
    }

    @Test
    void behavior() {
        Map<String, Object> data = data(new GetBehaviorTool(port).execute(new NoArgs(), full(TestCustomers.RETIREE)));

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
    void missingBlockIsOmitted() {
        ToolContext ctx = full(TestCustomers.RETIREE);

        assertUnavailable(new GetMemberRankTool(portWith(r -> with(r, r.customerManagement(), r.aum(), r.lum(), null)))
                .execute(new NoArgs(), ctx));

        Map<String, Object> noLiabilities = data(new GetAssetLiabilityTool(
                portWith(r -> with(r, r.customerManagement(), r.aum(), null, r.memberRank())))
                .execute(new NoArgs(), ctx));
        assertThat(noLiabilities).containsKey("assets").doesNotContainKeys("liabilities", "liabilityToAssetRatio");

        Map<String, Object> noAlerts = data(new GetManagementSummaryTool(
                portWith(r -> r, rt -> new RealTimeStatusRecord(false, false, null, List.of(), null)),
                new PiiRedactor()).execute(new NoArgs(), ctx));
        assertThat(noAlerts).containsEntry("keyAlerts", List.of());
    }

    @Test
    void missingCustomer360YieldsUnavailable() {
        ToolContext ctx = full(TestCustomers.UNKNOWN);
        for (AssistantTool<NoArgs> tool : allTools(port)) {
            assertUnavailable(tool.execute(new NoArgs(), ctx));
        }
    }

    private static List<AssistantTool<NoArgs>> allTools(CustomerDataPort p) {
        return List.of(new GetCustomerProfileTool(p), new GetManagementSummaryTool(p, new PiiRedactor()),
                new GetAssetLiabilityTool(p), new GetMemberRankTool(p), new GetProductIntentTool(p),
                new GetBehaviorTool(p));
    }

    @Test
    void accessLevels() {
        List<AssistantTool<NoArgs>> tools = allTools(port);
        assertThat(tools).filteredOn(t -> t.requiredAccessLevel() == AccessLevel.LIMITED)
                .extracting(AssistantTool::name).containsExactly("getCustomerProfile");
        assertThat(tools).extracting(AssistantTool::name).containsExactly("getCustomerProfile",
                "getManagementSummary", "getAssetLiability", "getMemberRank", "getProductIntent", "getBehavior");
        assertThat(tools).allSatisfy(t -> assertThat(t.timeout()).isEqualTo(Duration.ofSeconds(3)));

        List<AssistantTool<?>> all = new ArrayList<>(tools);
        all.add(new GetMarketIndexTool(null));
        all.add(new GetStockQuoteTool(null));
        all.add(new GetCompanyAnnouncementsTool(null));
        assertThat(new ToolRegistry(all).toolsFor(AccessLevel.LIMITED)).extracting(AssistantTool::name)
                .containsExactlyInAnyOrder("getCustomerProfile", "getMarketIndex", "getStockQuote",
                        "getCompanyAnnouncements");
    }

    @Test
    void citations() {
        ToolContext ctx = full(TestCustomers.RETIREE);
        Map<String, String> titles = Map.of(
                "getCustomerProfile", "個人360・顧客名片", "getManagementSummary", "個人360・顧客經營",
                "getAssetLiability", "個人360・資產負債", "getMemberRank", "個人360・會員等級",
                "getProductIntent", "個人360・產品意圖", "getBehavior", "個人360・往來與行為");

        for (AssistantTool<NoArgs> tool : allTools(port)) {
            ToolResult result = tool.execute(new NoArgs(), ctx);
            CitationDetail citation = result.citation();
            int blocks = (int) data(result).keySet().stream().filter(k -> !k.equals("dataDate")).count();
            assertThat(citation.title()).isEqualTo(titles.get(tool.name()));
            assertThat(citation.resultCount()).isEqualTo(blocks);
            assertThat(citation.excerpt()).isNull();
            assertThat(citation.target()).isNull();
        }
        // The LIMITED profile has four fields and no dataDate.
        assertThat(profileTool.execute(new NoArgs(), context(TestCustomers.RETIREE, AccessLevel.LIMITED))
                .citation().resultCount()).isEqualTo(4);
    }

    @Test
    void purposes() {
        assertThat(allTools(port)).extracting(AssistantTool::purpose).containsExactly("查詢顧客名片與註記",
                "查詢關鍵提醒與專屬活動", "查詢資產負債與產品結構", "查詢理財會員等級", "查詢財富潛力與產品意圖", "查詢往來與行為");
    }
}
