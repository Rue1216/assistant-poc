package com.example.copilot.aiassistant.infrastructure.market;

import com.example.copilot.aiassistant.domain.tool.market.TwseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.copilot.aiassistant.AiAssistantProperties;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TwseClientTest {

    private static final String BASE = "https://openapi.twse.com.tw/v1";
    private static final String URL = BASE + "/exchangeReport/MI_INDEX";
    private static final String BODY = "[{\"指數 \":\"A\",\"收盤指數\":\"1\"}]";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));

    private static AiAssistantProperties.Twse props(String baseUrl, int maxBytes) {
        return new AiAssistantProperties.Twse(baseUrl, List.of("openapi.twse.com.tw"), Duration.ofSeconds(3),
                Duration.ofSeconds(5), maxBytes, Duration.ofMinutes(10));
    }

    private TwseClient client(RestClient.Builder builder, String baseUrl, int maxBytes) {
        return new TwseClient(builder, props(baseUrl, maxBytes), clock);
    }

    @Test
    void trimsKeysOfFetchedRows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        List<Map<String, String>> rows = client(builder, BASE, 2_097_152).fetch(TwseClient.MI_INDEX);

        assertThat(rows).containsExactly(Map.of("指數", "A", "收盤指數", "1"));
        server.verify();
    }

    @Test
    void rejectsNonAllowlistedHost() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TwseClient client = client(builder, "https://evil.example.com/v1", 2_097_152);

        assertThatThrownBy(() -> client.fetch(TwseClient.MI_INDEX))
                .isInstanceOfSatisfying(TwseException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("MARKET_DATA_UNAVAILABLE"));
        server.verify();
    }

    @Test
    void rejectsPathOutsideTheFixedList() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TwseClient client = client(builder, BASE, 2_097_152);

        assertThatThrownBy(() -> client.fetch("/exchangeReport/OTHER"))
                .isInstanceOfSatisfying(TwseException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("MARKET_DATA_UNAVAILABLE"));
        server.verify();
    }

    @Test
    void timeoutMapsToMarketDataTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("read timed out");
        });
        TwseClient client = client(builder, BASE, 2_097_152);

        assertThatThrownBy(() -> client.fetch(TwseClient.MI_INDEX))
                .isInstanceOfSatisfying(TwseException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("MARKET_DATA_TIMEOUT"));
    }

    @Test
    void serverErrorMapsToUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withServerError());
        TwseClient client = client(builder, BASE, 2_097_152);

        assertThatThrownBy(() -> client.fetch(TwseClient.MI_INDEX))
                .isInstanceOfSatisfying(TwseException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("MARKET_DATA_UNAVAILABLE"));
    }

    @Test
    void malformedBodyMapsToUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>not json</html>", MediaType.APPLICATION_JSON));
        TwseClient client = client(builder, BASE, 2_097_152);

        assertThatThrownBy(() -> client.fetch(TwseClient.MI_INDEX))
                .isInstanceOfSatisfying(TwseException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("MARKET_DATA_UNAVAILABLE"));
    }

    @Test
    void oversizedBodyMapsToUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] big = ("[{\"a\":\"" + "x".repeat(2048 - 10) + "\"}]").getBytes(StandardCharsets.UTF_8);
        assertThat(big.length).isGreaterThanOrEqualTo(2040);
        server.expect(requestTo(URL)).andRespond(withSuccess(big, MediaType.APPLICATION_JSON));
        TwseClient client = client(builder, BASE, 1024);

        assertThatThrownBy(() -> client.fetch(TwseClient.MI_INDEX))
                .isInstanceOfSatisfying(TwseException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("MARKET_DATA_UNAVAILABLE"));
    }

    @Test
    void cachesForTenMinutes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        TwseClient client = client(builder, BASE, 2_097_152);

        client.fetch(TwseClient.MI_INDEX);
        clock.advance(Duration.ofMinutes(9));
        client.fetch(TwseClient.MI_INDEX); // served from cache: a second request would fail the mock server
        server.verify();

        server.reset();
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        clock.advance(Duration.ofMinutes(2));
        client.fetch(TwseClient.MI_INDEX);
        server.verify();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void emptyResponseIsNotCached() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        TwseClient client = client(builder, BASE, 2_097_152);

        assertThat(client.fetch(TwseClient.MI_INDEX)).isEmpty();
        assertThat(client.fetch(TwseClient.MI_INDEX)).hasSize(1);
        server.verify();
    }

    @Test
    void requestFactoryUsesConfiguredTimeouts() {
        JdkClientHttpRequestFactory factory = TwseClient.requestFactory(props(BASE, 1024));

        java.net.http.HttpClient http = (java.net.http.HttpClient) ReflectionTestUtils.getField(factory, "httpClient");
        assertThat(http.connectTimeout()).contains(Duration.ofSeconds(3));
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(Duration.ofSeconds(5));
    }
}
