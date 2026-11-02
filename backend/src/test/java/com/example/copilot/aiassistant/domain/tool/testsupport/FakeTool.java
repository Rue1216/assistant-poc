package com.example.copilot.aiassistant.domain.tool.testsupport;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.time.Duration;
import java.util.function.BiFunction;

/** Configurable test tool; never registered as a Spring bean. */
public class FakeTool<A extends Record> implements AssistantTool<A> {

    private final String name;
    private final Class<A> argsType;
    private final AccessLevel level;
    private final Duration timeout;
    private final BiFunction<A, ToolContext, ToolResult> body;

    public FakeTool(String name, Class<A> argsType, AccessLevel level, Duration timeout,
                    BiFunction<A, ToolContext, ToolResult> body) {
        this.name = name;
        this.argsType = argsType;
        this.level = level;
        this.timeout = timeout;
        this.body = body;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String purpose() {
        return "測試 " + name;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder().name(name).description("test").build();
    }

    @Override
    public Class<A> argsType() {
        return argsType;
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return level;
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public ToolResult execute(A args, ToolContext context) {
        return body.apply(args, context);
    }
}
