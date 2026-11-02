package com.example.copilot.aiassistant.domain.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Checks a model-issued tool call before anything runs. Failure texts are fixed (03 section 4). */
public class ToolGuard {

    static final int MAX_ARGUMENT_BYTES = 1024;
    static final String NOT_ALLOWED = "TOOL_NOT_ALLOWED";
    static final String ARGUMENT_INVALID = "TOOL_ARGUMENT_INVALID";

    private final ToolRegistry registry;
    private final Validator validator;
    private final JsonMapper strictMapper;

    public ToolGuard(ToolRegistry registry, Validator validator, JsonMapper mapper) {
        this.registry = registry;
        this.validator = validator;
        this.strictMapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    public sealed interface GuardResult permits Accepted, Rejected {
    }

    public record Accepted(AssistantTool<?> tool, Record args) implements GuardResult {
    }

    public record Rejected(ToolResult result) implements GuardResult {
    }

    public GuardResult check(ToolExecutionRequest request, Set<String> offeredToolNames) {
        String name = request.name();
        AssistantTool<?> tool = name == null || !offeredToolNames.contains(name)
                ? null : registry.find(name).orElse(null);
        if (tool == null) {
            return reject(NOT_ALLOWED, "此工具無法使用");
        }
        String raw = request.arguments() == null || request.arguments().isBlank() ? "{}" : request.arguments();
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_ARGUMENT_BYTES) {
            return reject(ARGUMENT_INVALID, "參數格式不正確");
        }
        try {
            Record args = (Record) strictMapper.readValue(raw, tool.argsType());
            if (args == null || !validator.validate(args).isEmpty()) {
                return reject(ARGUMENT_INVALID, "參數格式不正確");
            }
            return new Accepted(tool, args);
        } catch (RuntimeException e) {
            return reject(ARGUMENT_INVALID, "參數格式不正確");
        }
    }

    /** The purpose of a registered tool (offered or not), or empty for an unknown name. */
    Optional<String> purposeOf(String name) {
        return name == null ? Optional.empty() : registry.find(name).map(AssistantTool::purpose);
    }

    private static Rejected reject(String code, String message) {
        return new Rejected(ToolResult.failure(ToolStatus.DENIED, code, message));
    }
}
