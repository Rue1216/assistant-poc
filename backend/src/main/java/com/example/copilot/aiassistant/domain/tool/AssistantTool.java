package com.example.copilot.aiassistant.domain.tool;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.time.Duration;
import java.util.List;

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

    /** Choices a workflow marker may list after the tool name; empty when the tool takes none. */
    default List<ToolOption> options() {
        return List.of();
    }

    /** The argument the options fill, or null when the tool has no options. */
    default String optionsArgument() {
        return null;
    }

    /** Chinese request shown to the user when a required input is missing; null when nothing is required. */
    default String inputRequest() {
        return null;
    }

    /** Required arguments the options do not supply, so the user has to provide them. */
    default List<String> requiredInputs() {
        var parameters = specification().parameters();
        if (parameters == null || parameters.required() == null) {
            return List.of();
        }
        return parameters.required().stream().filter(name -> !name.equals(optionsArgument())).toList();
    }
}
