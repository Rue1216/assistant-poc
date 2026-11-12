package com.example.copilot.aiassistant.domain.tool.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.tool.CitationDetail;
import com.example.copilot.aiassistant.domain.tool.NoArgs;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import com.example.copilot.aiassistant.domain.tool.ToolStatus;
import com.example.copilot.aiassistant.domain.tool.market.GetCompanyAnnouncementsTool.AnnouncementArgs;
import com.example.copilot.aiassistant.domain.tool.market.GetCompanyAnnouncementsTool.AnnouncementsData;
import com.example.copilot.aiassistant.domain.tool.market.GetMarketIndexTool.IndexItem;
import com.example.copilot.aiassistant.domain.tool.market.GetMarketIndexTool.MarketIndexData;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool.StockArgs;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool.StockQuoteData;
import com.example.copilot.aiassistant.infrastructure.market.TwseClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MarketToolsTest {

    private static final String BASE = "https://openapi.twse.com.tw/v1";
    private static final ToolContext CONTEXT = new ToolContext(new UserContext("u1", "User", "ROLE", Set.of()),
            new CustomerContext(new CertNo(TestCustomers.RETIREE), AccessLevel.LIMITED, Set.of(), Set.of()));

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneOffset.UTC);

    private static String fixture(String name) throws IOException {
        try (var in = MarketToolsTest.class.getResourceAsStream("/twse/" + name + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private TwseClient client(RestClient.Builder builder) {
        AiAssistantProperties.Twse props = new AiAssistantProperties.Twse(BASE, List.of("openapi.twse.com.tw"),
                Duration.ofSeconds(3), Duration.ofSeconds(5), 2_097_152, Duration.ofMinutes(10));
        return new TwseClient(builder, props, clock);
    }

    /** A client whose endpoints answer with the named fixtures (or the given override bodies). */
    private TwseClient clientWith(String... pathAndBody) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        for (int i = 0; i < pathAndBody.length; i += 2) {
            server.expect(ExpectedCount.manyTimes(), requestTo(BASE + pathAndBody[i]))
                    .andRespond(withSuccess(pathAndBody[i + 1], MediaType.APPLICATION_JSON));
        }
        return client(builder);
    }

    private TwseClient fixtureClient() throws IOException {
        return clientWith(TwseClient.MI_INDEX, fixture("MI_INDEX"), TwseClient.STOCK_DAY_ALL, fixture("STOCK_DAY_ALL"),
                TwseClient.BWIBBU_ALL, fixture("BWIBBU_ALL"), TwseClient.COMPANY_ANNOUNCEMENTS,
                fixture("t187ap04_L"));
    }

    @Test
    void marketIndexReturnsFiveConfiguredIndices() throws IOException {
        ToolResult result = new GetMarketIndexTool(fixtureClient()).execute(new NoArgs(), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        MarketIndexData data = (MarketIndexData) result.data();
        assertThat(data.dataDate()).isEqualTo("2026/10/16");
        assertThat(data.indices()).extracting(IndexItem::name).containsExactly("發行量加權股價指數", "臺灣50指數",
                "寶島股價指數", "電子工業類指數", "金融保險類指數");
        IndexItem taiex = data.indices().get(0);
        assertThat(taiex.close()).isEqualByComparingTo("47111.26");
        assertThat(taiex.change()).isEqualByComparingTo("141.39");
        assertThat(taiex.changePercent()).isEqualByComparingTo("0.30");
        IndexItem electronics = data.indices().get(3);
        assertThat(electronics.change()).isEqualByComparingTo("-3.10");
        assertThat(result.sources()).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("臺灣證券交易所 OpenAPI");
            assertThat(s.url()).isEqualTo("https://openapi.twse.com.tw/");
            assertThat(s.dataTime()).isEqualTo("2026-10-16");
        });
    }

    @Test
    void emptyResponseYieldsEmptyData() throws IOException {
        ToolResult result = new GetMarketIndexTool(clientWith(TwseClient.MI_INDEX, "[]")).execute(new NoArgs(),
                CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        MarketIndexData data = (MarketIndexData) result.data();
        assertThat(data.indices()).isEmpty();
        assertThat(data.dataDate()).isNull();
    }

    @Test
    void stockQuoteMergesValuation() throws IOException {
        ToolResult result = new GetStockQuoteTool(fixtureClient()).execute(new StockArgs("2330"), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        StockQuoteData data = (StockQuoteData) result.data();
        assertThat(data.name()).isEqualTo("台積電");
        assertThat(data.dataDate()).isEqualTo("2026/10/16");
        assertThat(data.close()).isEqualByComparingTo("2420.00");
        assertThat(data.change()).isEqualByComparingTo("-20.00");
        assertThat(data.tradeVolume()).isEqualTo(43046982L);
        assertThat(data.peRatio()).isEqualByComparingTo("28.05");
        assertThat(data.dividendYield()).isEqualByComparingTo("0.91");
        assertThat(data.pbRatio()).isEqualByComparingTo("9.76");
    }

    @Test
    void missingValuationGivesNullFieldsNotAnError() throws IOException {
        TwseClient client = fixtureClient();

        StockQuoteData noPe = (StockQuoteData) new GetStockQuoteTool(client).execute(new StockArgs("2317"), CONTEXT)
                .data();
        StockQuoteData noRow = (StockQuoteData) new GetStockQuoteTool(client).execute(new StockArgs("0050"), CONTEXT)
                .data();

        assertThat(noPe.peRatio()).isNull();
        assertThat(noPe.dividendYield()).isEqualByComparingTo(new BigDecimal("3.20"));
        assertThat(noRow.close()).isEqualByComparingTo("190.80");
        assertThat(noRow.peRatio()).isNull();
        assertThat(noRow.dividendYield()).isNull();
        assertThat(noRow.pbRatio()).isNull();
    }

    @Test
    void unknownStockIsNotFound() throws IOException {
        ToolResult result = new GetStockQuoteTool(fixtureClient()).execute(new StockArgs("9999"), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("STOCK_NOT_FOUND");
        assertThat(result.message()).isEqualTo("查無此上市股票代號");
    }

    @Test
    void announcementsTrimKeysAndSubject() throws IOException {
        ToolResult result = new GetCompanyAnnouncementsTool(fixtureClient())
                .execute(new AnnouncementArgs("2072", null), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        AnnouncementsData data = (AnnouncementsData) result.data();
        assertThat(data.companyName()).isEqualTo("世紀風電");
        assertThat(data.announcements()).hasSize(1);
        var item = data.announcements().get(0);
        assertThat(item.subject()).doesNotContain("\r", "\n").startsWith("公告本公司名稱由");
        assertThat(item.subject().length()).isEqualTo(200);
        assertThat(item.date()).isEqualTo("2026/10/18");
        assertThat(item.time()).isEqualTo("07:00:03");
        assertThat(item.factDate()).isEqualTo("2026/08/24");
    }

    @Test
    void noAnnouncementsIsSuccessWithEmptyList() throws IOException {
        ToolResult result = new GetCompanyAnnouncementsTool(fixtureClient())
                .execute(new AnnouncementArgs("2330", 3), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        AnnouncementsData data = (AnnouncementsData) result.data();
        assertThat(data.announcements()).isEmpty();
        assertThat(data.stockCode()).isEqualTo("2330");
    }

    @Test
    void announcementsRespectLimit() throws IOException {
        String rows = "[" + "{\"發言日期\":\"1151018\",\"發言時間\":\"90000\",\"公司代號\":\"2330\",\"公司名稱\":\"台積電\","
                + "\"主旨 \":\"A\",\"事實發生日\":\"1151018\"},"
                + "{\"發言日期\":\"1151018\",\"發言時間\":\"100000\",\"公司代號\":\"2330\",\"公司名稱\":\"台積電\","
                + "\"主旨 \":\"B\",\"事實發生日\":\"1151018\"}]";
        TwseClient client = clientWith(TwseClient.COMPANY_ANNOUNCEMENTS, rows);

        AnnouncementsData data = (AnnouncementsData) new GetCompanyAnnouncementsTool(client)
                .execute(new AnnouncementArgs("2330", 1), CONTEXT).data();

        assertThat(data.announcements()).singleElement().extracting("subject").isEqualTo("B");
    }

    @Test
    void twseTimeoutBecomesTimeoutFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + TwseClient.MI_INDEX)).andRespond(request -> {
            throw new SocketTimeoutException("read timed out");
        });

        ToolResult result = new GetMarketIndexTool(client(builder)).execute(new NoArgs(), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.TIMEOUT);
        assertThat(result.errorCode()).isEqualTo("MARKET_DATA_TIMEOUT");
        assertThat(result.message()).isEqualTo("市場資料查詢逾時");
    }

    @Test
    void twseServerErrorBecomesUnavailableFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + TwseClient.STOCK_DAY_ALL)).andRespond(withServerError());

        ToolResult result = new GetStockQuoteTool(client(builder)).execute(new StockArgs("2330"), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("MARKET_DATA_UNAVAILABLE");
        assertThat(result.message()).isEqualTo("市場資料暫時無法取得");
    }

    @Test
    void toolsAreMarketToolsWithLimitedAccessAndFiveSecondTimeout() throws IOException {
        TwseClient client = fixtureClient();
        for (var tool : List.of(new GetMarketIndexTool(client), new GetStockQuoteTool(client),
                new GetCompanyAnnouncementsTool(client))) {
            assertThat(tool).isInstanceOf(MarketTool.class);
            assertThat(tool.requiredAccessLevel()).isEqualTo(AccessLevel.LIMITED);
            assertThat(tool.timeout()).isEqualTo(Duration.ofSeconds(5));
        }
    }

    @Test
    void quoteStillReturnedWhenValuationFetchFails() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(requestTo(BASE + TwseClient.STOCK_DAY_ALL))
                .andRespond(withSuccess(fixture("STOCK_DAY_ALL"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + TwseClient.BWIBBU_ALL)).andRespond(withServerError());

        ToolResult result = new GetStockQuoteTool(client(builder)).execute(new StockArgs("2330"), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        StockQuoteData data = (StockQuoteData) result.data();
        assertThat(data.close()).isEqualByComparingTo("2420.00");
        assertThat(data.peRatio()).isNull();
        assertThat(data.dividendYield()).isNull();
        assertThat(data.pbRatio()).isNull();
    }

    @Test
    void emptyStockResponseIsNotFound() {
        TwseClient client = clientWith(TwseClient.STOCK_DAY_ALL, "[]", TwseClient.BWIBBU_ALL, "[]");

        ToolResult result = new GetStockQuoteTool(client).execute(new StockArgs("2330"), CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("STOCK_NOT_FOUND");
    }

    @Test
    void emptyAnnouncementResponseIsSuccessWithEmptyList() {
        TwseClient client = clientWith(TwseClient.COMPANY_ANNOUNCEMENTS, "[]");

        ToolResult result = new GetCompanyAnnouncementsTool(client).execute(new AnnouncementArgs("2330", null),
                CONTEXT);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(((AnnouncementsData) result.data()).announcements()).isEmpty();
    }

    @Test
    void stockQuoteCitationSummarisesQuote() throws IOException {
        TwseClient client = fixtureClient();

        assertThat(new GetStockQuoteTool(client).execute(new StockArgs("2330"), CONTEXT).citation())
                .isEqualTo(new CitationDetail("上市個股行情", "台積電 2330",
                        "收盤 2420、漲跌 -20、本益比 28.05、殖利率 0.91%", null, 1));
        CitationDetail noValuation = new GetStockQuoteTool(client).execute(new StockArgs("0050"), CONTEXT)
                .citation();
        assertThat(noValuation.excerpt()).startsWith("收盤 190.8");
        assertThat(noValuation.excerpt()).doesNotContain("本益比", "殖利率");
    }

    @Test
    void marketIndexCitationListsThreeItems() throws IOException {
        CitationDetail citation = new GetMarketIndexTool(fixtureClient()).execute(new NoArgs(), CONTEXT).citation();

        assertThat(citation.title()).isEqualTo("大盤指數");
        assertThat(citation.target()).isNull();
        assertThat(citation.resultCount()).isEqualTo(5);
        assertThat(citation.excerpt()).startsWith("加權指數 47111.26（漲跌 +141.39）、");
        assertThat(citation.excerpt().split("、")).hasSize(3);
    }

    @Test
    void announcementsCitationUsesSubjectsAndDataTime() throws IOException {
        TwseClient client = fixtureClient();

        ToolResult some = new GetCompanyAnnouncementsTool(client).execute(new AnnouncementArgs("2072", null), CONTEXT);
        assertThat(some.citation().title()).isEqualTo("上市公司重大訊息");
        assertThat(some.citation().target()).isEqualTo("世紀風電 2072");
        assertThat(some.citation().resultCount()).isEqualTo(1);
        assertThat(some.citation().excerpt()).startsWith("公告本公司名稱由");
        assertThat(some.citation().excerpt().codePointCount(0, some.citation().excerpt().length())).isEqualTo(60);

        ToolResult none = new GetCompanyAnnouncementsTool(client).execute(new AnnouncementArgs("2330", 3), CONTEXT);
        assertThat(none.citation()).isEqualTo(
                new CitationDetail("上市公司重大訊息", "2330", "查無重大訊息", null, 0));
        assertThat(none.sources().get(0).dataTime()).isNull();
    }
}
