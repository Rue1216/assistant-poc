package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.tool.NoArgs;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetCustomerProfileTool extends AbstractCustomerTool<NoArgs> {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final Clock clock;

    public GetCustomerProfileTool(CustomerDataPort port, Clock clock) {
        super(port);
        this.clock = clock;
    }

    @Override
    public String name() {
        return "getCustomerProfile";
    }

    @Override
    public String purpose() {
        return "查詢顧客概況與標籤";
    }

    @Override
    public ToolSpecification specification() {
        return spec(name(), "查詢本顧客的去識別化概況：客群、年齡區間、往來年資、資產級距、風險屬性、行銷同意、偏好管道與既有標籤。不需要參數。")
                .parameters(schema().build()).build();
    }

    @Override
    public Class<NoArgs> argsType() {
        return NoArgs.class;
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return AccessLevel.LIMITED;
    }

    @Override
    public ToolResult execute(NoArgs args, ToolContext context) {
        LocalDate today = LocalDate.now(clock.withZone(TAIPEI));
        return customer(context)
                .map(r -> profileResult(CustomerDeidentifier.profile(r, context.customer().accessLevel(), today)))
                .orElseGet(AbstractCustomerTool::unavailable);
    }

    private static ToolResult profileResult(Map<String, Object> profile) {
        // "subject" and "accessNote" are labels, not profile fields.
        int fields = (int) profile.keySet().stream()
                .filter(k -> !k.equals("subject") && !k.equals("accessNote")).count();
        return ToolResult.success(profile, SOURCES, citation("顧客概況", fields + " 個欄位", fields));
    }
}
