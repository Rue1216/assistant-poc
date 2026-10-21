package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.tool.AssistantTool;
import com.example.copilot.aiassistant.tool.Source;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Shared parts of the four customer tools; the customer always comes from the bound context. */
abstract class AbstractCustomerTool<A extends Record> implements AssistantTool<A> {

    static final List<Source> SOURCES = List.of(new Source("平台顧客資料（模擬）", null, null));

    protected final CustomerDataPort port;

    AbstractCustomerTool(CustomerDataPort port) {
        this.port = port;
    }

    static ToolSpecification.Builder spec(String name, String description) {
        return ToolSpecification.builder().name(name).description(description);
    }

    static JsonObjectSchema.Builder schema() {
        return JsonObjectSchema.builder().additionalProperties(false);
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(3);
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return AccessLevel.FULL;
    }

    protected Optional<CustomerRecord> customer(ToolContext context) {
        return port.findCustomer(context.customer().customerId());
    }

    protected static ToolResult unavailable() {
        return ToolResult.failure(ToolStatus.ERROR, "CUSTOMER_DATA_UNAVAILABLE", "目前無法取得本顧客資料");
    }
}
