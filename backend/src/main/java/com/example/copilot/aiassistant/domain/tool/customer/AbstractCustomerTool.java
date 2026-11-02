package com.example.copilot.aiassistant.domain.tool.customer;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.Source;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import com.example.copilot.aiassistant.domain.tool.ToolStatus;
import java.time.Duration;
import java.util.List;

/** Shared parts of the customer-360 tools; the customer and its data always come from the tool context. */
abstract class AbstractCustomerTool<A extends Record> implements AssistantTool<A> {

    static final String TITLE_PREFIX = "個人360・";
    static final List<Source> SOURCES = List.of(new Source("平台顧客資料（模擬）", null, null));

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(3);
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return AccessLevel.FULL;
    }

    protected static ToolResult unavailable() {
        return ToolResult.failure(ToolStatus.ERROR, "CUSTOMER_DATA_UNAVAILABLE", "目前無法取得本顧客資料");
    }
}
