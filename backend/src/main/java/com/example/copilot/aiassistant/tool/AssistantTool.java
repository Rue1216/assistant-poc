package com.example.copilot.aiassistant.tool;

import com.example.copilot.aiassistant.port.AccessLevel;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.time.Duration;

/** A read-only tool the model may call. Arguments arrive already parsed and validated as {@code A}. */
public interface AssistantTool<A extends Record> {

    String name();

    /** Chinese purpose shown in the answer summary. */
    String purpose();

    ToolSpecification specification();

    Class<A> argsType();

    AccessLevel requiredAccessLevel();

    Duration timeout();

    ToolResult execute(A args, ToolContext context);
}
