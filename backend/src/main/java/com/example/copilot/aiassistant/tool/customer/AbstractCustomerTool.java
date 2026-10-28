package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.tool.AssistantTool;
import com.example.copilot.aiassistant.tool.CitationDetail;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.Source;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared parts of the customer-360 tools; the customer always comes from the bound context. */
abstract class AbstractCustomerTool implements AssistantTool<NoArgs> {

    static final String TITLE_PREFIX = "個人360・";
    static final List<Source> SOURCES = List.of(new Source("平台顧客資料（模擬）", null, null));

    private static final Logger log = LoggerFactory.getLogger(AbstractCustomerTool.class);

    protected final CustomerDataPort port;

    AbstractCustomerTool(CustomerDataPort port) {
        this.port = port;
    }

    /** A tool without parameters. */
    static ToolSpecification noArgsSpec(String name, String description) {
        return ToolSpecification.builder().name(name).description(description)
                .parameters(JsonObjectSchema.builder().additionalProperties(false).build()).build();
    }

    @Override
    public Class<NoArgs> argsType() {
        return NoArgs.class;
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(3);
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return AccessLevel.FULL;
    }

    protected Optional<Customer360Record> customer360(ToolContext context) {
        return port.findCustomer360(context.customer().certNo());
    }

    protected Optional<RealTimeStatusRecord> realTime(ToolContext context) {
        return port.findRealTimeStatus(context.customer().certNo());
    }

    /** Internal data: the citation carries only a title and the number of top-level blocks, never an excerpt. */
    protected static ToolResult success(Map<String, Object> data, String titleSuffix) {
        int blocks = (int) data.keySet().stream().filter(k -> !k.equals("dataDate")).count();
        return ToolResult.success(data, SOURCES,
                new CitationDetail(TITLE_PREFIX + titleSuffix, null, null, blocks + " 個欄位", blocks));
    }

    protected static ToolResult unavailable() {
        return ToolResult.failure(ToolStatus.ERROR, "CUSTOMER_DATA_UNAVAILABLE", "目前無法取得本顧客資料");
    }

    /** Logs the name of a block the platform did not deliver; never any customer data. */
    protected static void missing(String block) {
        log.warn("customer 360 block missing: {}", block);
    }

    /** Parses a platform number string; null (and a warn log without the value) when absent or malformed. */
    protected static BigDecimal decimal(String text, String field) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            log.warn("customer 360 number malformed: {}", field);
            return null;
        }
    }
}
