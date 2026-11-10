package com.example.copilot.aiassistant.domain.tool;

import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Runs one accepted tool call on a virtual thread and builds the 03 section 1.5 payload for the model.
 * Redaction covers string values only; map keys are not redacted, so tools must use fixed keys.
 */
@Slf4j
public class ToolExecutor {

    private static final String TRACE_ID_KEY = "traceId";
    private static final int MAX_NAME_LENGTH = 64;
    private static final String UNKNOWN_PURPOSE = "未知工具";

    private final ToolGuard guard;
    private final PiiRedactor redactor;
    private final JsonMapper mapper;
    private final Duration timeoutCeiling;
    private final int maxBytes;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ToolExecutor(ToolGuard guard, PiiRedactor redactor, JsonMapper mapper, Duration toolTimeout,
                        int toolResultMaxBytes) {
        this.guard = guard;
        this.redactor = redactor;
        this.mapper = mapper;
        this.timeoutCeiling = toolTimeout;
        this.maxBytes = toolResultMaxBytes;
    }

    public ToolOutcome execute(ToolExecutionRequest request, ToolContext context, Set<String> offeredToolNames,
                               CitationIds ids) {
        long start = System.nanoTime();
        ToolGuard.GuardResult checked = guard.check(request, offeredToolNames);
        if (checked instanceof ToolGuard.Rejected rejected) {
            return outcome(safeName(request.name()), UNKNOWN_PURPOSE, rejected.result(), context, start, ids);
        }
        ToolGuard.Accepted accepted = (ToolGuard.Accepted) checked;
        AssistantTool<?> tool = accepted.tool();
        ToolResult result = run(tool, accepted.args(), context);
        return outcome(tool.name(), tool.purpose(), result, context, start, ids);
    }

    /** The answer for a call beyond the per-question cap: not executed, but the model still gets a result for it. */
    public ToolOutcome limitReached(ToolExecutionRequest request, ToolContext context) {
        String purpose = guard.purposeOf(request.name()).orElse(UNKNOWN_PURPOSE);
        ToolResult result = ToolResult.failure(ToolStatus.DENIED, "TOOL_CALL_LIMIT_REACHED", "已達本次查詢次數上限，未執行");
        ToolOutcome built = outcome(safeName(request.name()), purpose, result, context, System.nanoTime(), null);
        // Not executed, so no duration to measure.
        return new ToolOutcome(built.toolName(), built.purpose(), built.result(), built.payloadForModel(), 0, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolResult run(AssistantTool tool, Record args, ToolContext context) {
        Duration timeout = tool.timeout().compareTo(timeoutCeiling) < 0 ? tool.timeout() : timeoutCeiling;
        Future<ToolResult> future = executor.submit(() -> (ToolResult) tool.execute(args, context));
        try {
            ToolResult result = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return result != null ? result : executionFailed();
        } catch (TimeoutException e) {
            future.cancel(true);
            return ToolResult.failure(ToolStatus.TIMEOUT, "TOOL_TIMEOUT", "查詢逾時");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            logFailure("interrupted", tool.name(), e);
            return executionFailed();
        } catch (ExecutionException | RuntimeException e) {
            logFailure("execution failed", tool.name(), e);
            return executionFailed();
        }
    }

    /**
     * Logs a failure without any exception message: messages (and ExecutionException's cause.toString()) can echo
     * tool or parser input, which may be customer data. Only class names and stack frames are logged.
     */
    private static void logFailure(String what, String toolName, Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        RuntimeException safe = new RuntimeException(root.getClass().getName());
        safe.setStackTrace(root.getStackTrace());
        log.error("traceId={} tool={} {} ({})", MDC.get(TRACE_ID_KEY), toolName, what,
                error.getClass().getName(), safe);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static String safeName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    private static ToolResult executionFailed() {
        return ToolResult.failure(ToolStatus.ERROR, "TOOL_EXECUTION_FAILED", "查詢失敗");
    }

    /** {@code ids} may be null (call not executed): no citation id is issued then. */
    private ToolOutcome outcome(String name, String purpose, ToolResult result, ToolContext context, long start,
                                CitationIds ids) {
        // The id is only consumed once the payload was built, so a failed payload does not leave a gap.
        String citationId = ids != null && result.status() == ToolStatus.SUCCESS ? ids.peek() : null;
        String payload;
        try {
            payload = payload(result, context, citationId);
        } catch (RuntimeException e) {
            logFailure("could not build model payload", name, e);
            result = executionFailed();
            citationId = null;
            payload = payload(result, context, null);
        }
        if (citationId != null) {
            ids.next();
        }
        return new ToolOutcome(name, purpose, result, payload, (System.nanoTime() - start) / 1_000_000, citationId);
    }

    private String payload(ToolResult result, ToolContext context, String citationId) {
        ObjectNode root = mapper.createObjectNode();
        root.put("untrustedData", true);
        root.put("status", result.status().name());
        if (result.status() != ToolStatus.SUCCESS) {
            root.put("errorCode", result.errorCode());
            root.put("message", redactor.redact(result.message(), context.customer()));
            return mapper.writeValueAsString(root);
        }
        if (citationId != null) {
            root.put("citationId", citationId);
        }
        JsonNode data = redactStrings(mapper.valueToTree(result.data()), context);
        root.set("data", data);
        String json = mapper.writeValueAsString(root);
        boolean truncated = false;
        while (bytes(json) > maxBytes) {
            ArrayNode largest = largestNonEmptyArray(data);
            if (largest == null) {
                throw new IllegalStateException("tool result exceeds the size limit and has no list to truncate");
            }
            largest.remove(largest.size() - 1);
            if (!truncated) {
                root.put("truncated", true);
                truncated = true;
            }
            json = mapper.writeValueAsString(root);
        }
        return json;
    }

    private JsonNode redactStrings(JsonNode node, ToolContext context) {
        if (node.isString()) {
            return StringNode.valueOf(redactor.redact(node.asString(), context.customer()));
        }
        if (node instanceof ObjectNode object) {
            for (String field : object.propertyNames().toArray(String[]::new)) {
                object.set(field, redactStrings(object.get(field), context));
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, redactStrings(array.get(i), context));
            }
        }
        return node;
    }

    /** The array with the largest serialized size anywhere in the tree, or null when every array is empty. */
    private ArrayNode largestNonEmptyArray(JsonNode node) {
        ArrayNode best = null;
        int bestSize = 0;
        if (node instanceof ArrayNode array && array.size() > 0) {
            best = array;
            bestSize = bytes(array.toString());
        }
        for (JsonNode child : node.values()) {
            ArrayNode candidate = largestNonEmptyArray(child);
            if (candidate != null && bytes(candidate.toString()) > bestSize) {
                best = candidate;
                bestSize = bytes(candidate.toString());
            }
        }
        return best;
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
