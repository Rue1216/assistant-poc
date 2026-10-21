package com.example.copilot.aiassistant.tool.market;

import com.example.copilot.aiassistant.AiAssistantProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only client for the four TWSE OpenAPI endpoints (03 section 3.1). Paths are constants, the host must be
 * allow-listed (checked before any I/O), the body is size-limited and each endpoint is cached as a whole.
 */
@Component
public class TwseClient {

    public static final String MI_INDEX = "/exchangeReport/MI_INDEX";
    public static final String STOCK_DAY_ALL = "/exchangeReport/STOCK_DAY_ALL";
    public static final String BWIBBU_ALL = "/exchangeReport/BWIBBU_ALL";
    public static final String COMPANY_ANNOUNCEMENTS = "/opendata/t187ap04_L";

    private static final Set<String> PATHS = Set.of(MI_INDEX, STOCK_DAY_ALL, BWIBBU_ALL, COMPANY_ANNOUNCEMENTS);
    private static final TypeReference<List<Map<String, String>>> ROWS = new TypeReference<>() {
    };

    private record CachedEntry(Instant fetchedAt, List<Map<String, String>> rows) {
    }

    private final RestClient restClient;
    private final int maxResponseBytes;
    private final Duration cacheTtl;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public TwseClient(AiAssistantProperties props, Clock clock) {
        this(jdkBuilder(props.market().twse()), props.market().twse(), clock);
    }

    /** The builder supplies the request factory; this adds the base URL and the host allow-list interceptor. */
    public TwseClient(RestClient.Builder builder, AiAssistantProperties.Twse twse, Clock clock) {
        Set<String> allowedHosts = Set.copyOf(twse.allowedHosts());
        ClientHttpRequestInterceptor allowList = (request, body, execution) -> {
            String host = request.getURI().getHost();
            if (host == null || !allowedHosts.contains(host.toLowerCase())) {
                throw new TwseException(TwseException.UNAVAILABLE);
            }
            return execution.execute(request, body);
        };
        this.restClient = builder.baseUrl(twse.baseUrl()).requestInterceptor(allowList).build();
        this.maxResponseBytes = twse.maxResponseBytes();
        this.cacheTtl = twse.cacheTtl();
        this.clock = clock;
    }

    private static RestClient.Builder jdkBuilder(AiAssistantProperties.Twse twse) {
        return RestClient.builder().requestFactory(requestFactory(twse));
    }

    static JdkClientHttpRequestFactory requestFactory(AiAssistantProperties.Twse twse) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(twse.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(twse.readTimeout());
        return factory;
    }

    /** Rows of one endpoint with trimmed keys, from cache when younger than the TTL. */
    public List<Map<String, String>> fetch(String path) {
        if (!PATHS.contains(path)) {
            throw new TwseException(TwseException.UNAVAILABLE);
        }
        Instant now = clock.instant();
        CachedEntry cached = cache.get(path);
        if (cached != null && now.isBefore(cached.fetchedAt().plus(cacheTtl))) {
            return cached.rows();
        }
        List<Map<String, String>> rows = load(path);
        if (!rows.isEmpty()) { // an empty list (holiday, data not yet published) is retried next time
            cache.put(path, new CachedEntry(now, rows));
        }
        return rows;
    }

    private List<Map<String, String>> load(String path) {
        try {
            byte[] body = restClient.get().uri(path).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new TwseException(TwseException.UNAVAILABLE);
                }
                try (InputStream in = response.getBody()) {
                    byte[] bytes = in.readNBytes(maxResponseBytes + 1);
                    if (bytes.length > maxResponseBytes) {
                        throw new TwseException(TwseException.UNAVAILABLE);
                    }
                    return bytes;
                }
            });
            return normalize(mapper.readValue(body, ROWS));
        } catch (TwseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TwseException(isTimeout(e) ? TwseException.TIMEOUT : TwseException.UNAVAILABLE, e);
        }
    }

    private static List<Map<String, String>> normalize(List<Map<String, String>> raw) {
        if (raw == null) {
            throw new TwseException(TwseException.UNAVAILABLE);
        }
        return raw.stream().map(row -> {
            Map<String, String> trimmed = new LinkedHashMap<>();
            row.forEach((k, v) -> trimmed.put(k.trim(), v == null ? "" : v));
            return Map.copyOf(trimmed);
        }).toList();
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException
                    || t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
