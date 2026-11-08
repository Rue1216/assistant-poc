package com.example.copilot.aiassistant.application.chat;

import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.domain.config.WorkflowValidator;
import com.example.copilot.aiassistant.domain.prompt.WorkflowStep;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.CitationIds;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolExecutor;
import com.example.copilot.aiassistant.domain.tool.ToolGuard;
import com.example.copilot.aiassistant.domain.tool.ToolOutcome;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.aiassistant.domain.tool.ToolStatus;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns an instruction's workflow steps into tool calls and runs them through {@link ToolExecutor}, so that they
 * pass the same permission check, timeout, redaction and size limit as the calls the model makes. A step that fails
 * does not stop the others; only a required input that cannot be found stops the whole workflow before anything runs.
 */
@Component
@Slf4j
public class WorkflowRunner {

    private static final String GENERIC_INPUT_REQUEST = "請提供查詢所需的資料。";
    private static final String UNKNOWN_PURPOSE = "未知工具";

    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final ToolGuard guard;
    private final StepArgumentExtractor extractor;
    private final JsonMapper mapper;

    public WorkflowRunner(ToolRegistry registry, ToolExecutor executor, ToolGuard guard,
                          StepArgumentExtractor extractor, JsonMapper mapper) {
        this.registry = registry;
        this.executor = executor;
        this.guard = guard;
        this.extractor = extractor;
        this.mapper = mapper;
    }

    /**
     * inputRequests is non-empty when a required input is missing: nothing may run then. droppedNotices holds one
     * notice per step whose tool is not registered; such a step neither runs nor reaches the model.
     */
    public record Prepared(List<ToolExecutionRequest> requests, List<String> inputRequests,
                           StepArgumentExtractor.Result extraction, List<String> droppedNotices) {
    }

    public record StepExecution(ToolExecutionRequest request, ToolOutcome outcome) {
    }

    /**
     * Builds one request per step. A tool outside {@code offered} gets no extraction: the executor denies its step.
     * A step whose tool is not registered is dropped: its name may be any text, and the provider rejects a whole
     * request over an invalid function name (spec 3.3).
     *
     * @param summary the de-identified summary of earlier turns, or null when there is none
     */
    public Prepared prepare(List<WorkflowStep> steps, String redactedQuestion, List<ChatMessage> redactedHistory,
                            String summary, Set<String> offered) {
        List<WorkflowStep> kept = withinLimit(steps);
        List<AssistantTool<?>> needInput = kept.stream()
                .filter(step -> offered.contains(step.toolName()))
                .flatMap(step -> registry.find(step.toolName()).stream())
                .filter(tool -> !tool.requiredInputs().isEmpty())
                .toList();
        StepArgumentExtractor.Result extraction =
                extractor.extract(redactedQuestion, redactedHistory, summary, needInput);

        List<ToolExecutionRequest> requests = new ArrayList<>();
        Set<String> inputRequests = new LinkedHashSet<>();
        List<String> droppedNotices = new ArrayList<>();
        for (WorkflowStep step : kept) {
            AssistantTool<?> tool = registry.find(step.toolName()).orElse(null);
            if (tool == null) {
                // A reason code only: the marker text is instruction content.
                log.warn("workflow step dropped requestId={} reason=TOOL_NOT_REGISTERED",
                        RequestContext.currentRequestId());
                droppedNotices.add(notice(UNKNOWN_PURPOSE));
                continue;
            }
            String extracted = extraction.argumentsByTool().get(step.toolName());
            ToolExecutionRequest request = ToolExecutionRequest.builder().id("workflow-" + (requests.size() + 1))
                    .name(step.toolName()).arguments(arguments(step, tool, extracted)).build();
            requests.add(request);
            // Only an extracted value is judged here: a bad fixed option is the admin's, so that step just fails.
            if (extracted != null && guard.check(request, offered) instanceof ToolGuard.Rejected) {
                inputRequests.add(tool.inputRequest() == null ? GENERIC_INPUT_REQUEST : tool.inputRequest());
            }
        }
        return new Prepared(inputRequests.isEmpty() ? List.copyOf(requests) : List.of(), List.copyOf(inputRequests),
                extraction, List.copyOf(droppedNotices));
    }

    /** Runs the requests in order, whatever the earlier ones returned; {@code beforeEachStep} may throw to stop. */
    public List<StepExecution> execute(List<ToolExecutionRequest> requests, ToolContext context, Set<String> offered,
                                       CitationIds ids, Runnable beforeEachStep) {
        List<StepExecution> executions = new ArrayList<>();
        for (ToolExecutionRequest request : requests) {
            beforeEachStep.run();
            executions.add(new StepExecution(request, executor.execute(request, context, offered, ids)));
        }
        return executions;
    }

    /** One "未能完成「purpose」，以下回答可能不完整。" per step whose status is not SUCCESS. */
    public List<String> failureNotices(List<StepExecution> executions) {
        return executions.stream()
                .filter(execution -> execution.outcome().result().status() != ToolStatus.SUCCESS)
                .map(execution -> notice(purposeOf(execution)))
                .toList();
    }

    private static String notice(String purpose) {
        return "未能完成「" + purpose + "」，以下回答可能不完整。";
    }

    /**
     * The executor reports a rejected call as an unknown tool, so the purpose comes from the registry. A request from
     * {@link #prepare} always names a registered tool, and the turn keeps that name unredacted.
     */
    private String purposeOf(StepExecution execution) {
        return registry.find(execution.request().name()).orElseThrow().purpose();
    }

    /** Only content published around the validator can exceed the limit; running what fits beats failing the turn. */
    private static List<WorkflowStep> withinLimit(List<WorkflowStep> steps) {
        if (steps.size() <= WorkflowValidator.MAX_STEPS) {
            return steps;
        }
        // Counts only: marker text is instruction content.
        log.warn("workflow steps cut requestId={} steps={} kept={}", RequestContext.currentRequestId(), steps.size(),
                WorkflowValidator.MAX_STEPS);
        return steps.subList(0, WorkflowValidator.MAX_STEPS);
    }

    /** The fixed options plus the extracted inputs; an empty object when the step has neither. */
    private String arguments(WorkflowStep step, AssistantTool<?> tool, String extracted) {
        ObjectNode arguments = mapper.createObjectNode();
        if (tool.optionsArgument() != null) {
            ArrayNode options = arguments.putArray(tool.optionsArgument());
            step.options().forEach(options::add);
        }
        if (extracted != null) {
            arguments.setAll((ObjectNode) mapper.readTree(extracted));
        }
        return mapper.writeValueAsString(arguments);
    }
}
