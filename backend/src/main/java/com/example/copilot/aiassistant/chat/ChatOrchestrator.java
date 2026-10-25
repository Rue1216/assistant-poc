package com.example.copilot.aiassistant.chat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.answer.AnswerAssembler;
import com.example.copilot.aiassistant.answer.AnswerSchema;
import com.example.copilot.aiassistant.answer.AssembledAnswer;
import com.example.copilot.aiassistant.answer.BlockType;
import com.example.copilot.aiassistant.audit.AuditEntry;
import com.example.copilot.aiassistant.audit.AuditLogger;
import com.example.copilot.aiassistant.chat.ChatCommand.HistoryMessage;
import com.example.copilot.aiassistant.chat.ChatResult.CitationView;
import com.example.copilot.aiassistant.chat.ChatResult.ToolCallSummary;
import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.common.RequestIdFilter;
import com.example.copilot.aiassistant.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.prompt.AssembledPrompt;
import com.example.copilot.aiassistant.prompt.PromptAssembler;
import com.example.copilot.aiassistant.tool.AssistantTool;
import com.example.copilot.aiassistant.tool.CitationDetail;
import com.example.copilot.aiassistant.tool.CitationIds;
import com.example.copilot.aiassistant.tool.Source;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolExecutor;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import com.example.copilot.aiassistant.tool.market.MarketTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The tool-calling loop (01 section 3). Every text that reaches the model is either the assembled system message,
 * a {@link PiiRedactor}-processed user/assistant text, or a payload built by {@link ToolExecutor}; the customer is
 * never named. Every model request carries the answer schema; {@link AnswerAssembler} turns the final JSON into
 * blocks and citations (answer-citations spec 2). The application log carries counters only, never question,
 * answer, raw model output or tool data (05 section 5).
 */
@Component
public class ChatOrchestrator {

    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final ChatModel chatModel;
    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final PiiRedactor redactor;
    private final PromptAssembler promptAssembler;
    private final AnswerAssembler answerAssembler;
    private final AuditLogger auditLogger;
    private final LlmErrorMapper errorMapper;
    private final AiAssistantProperties.Limits limits;
    private final Clock clock;

    public ChatOrchestrator(ChatModel chatModel, ToolRegistry registry, ToolExecutor executor, PiiRedactor redactor,
                            PromptAssembler promptAssembler, AnswerAssembler answerAssembler,
                            AuditLogger auditLogger, LlmErrorMapper errorMapper, AiAssistantProperties props,
                            Clock clock) {
        this.chatModel = chatModel;
        this.registry = registry;
        this.executor = executor;
        this.redactor = redactor;
        this.promptAssembler = promptAssembler;
        this.answerAssembler = answerAssembler;
        this.auditLogger = auditLogger;
        this.errorMapper = errorMapper;
        this.limits = props.limits();
        this.clock = clock;
    }

    /** Answers one question; on failure writes a FAILED audit entry and throws {@link AssistantException}. */
    public ChatResult chat(ChatCommand command, UserContext user, CustomerContext customer) {
        Turn turn = new Turn(user, customer, promptAssembler.current());
        try {
            ChatResult result = turn.result(turn.answer(command));
            turn.audit(turn.assembled, "ANSWERED", null);
            long citedBlocks = result.blocks().stream()
                    .filter(b -> b.type() != BlockType.HEADING && !b.citations().isEmpty()).count();
            log.info("chat answered requestId={} userId={} durationMs={} modelCalls={} toolCalls={} totalTokens={} "
                            + "inputTokens={} outputTokens={} "
                            + "blocks={} citedBlocks={} peakOutputTokens={}", turn.requestId, user.userId(),
                    turn.elapsedMs(), turn.modelCalls, turn.executed.size(), turn.totalTokens, turn.inputTokens,
                    turn.outputTokens, result.blocks().size(),
                    citedBlocks, turn.peakOutputTokens);
            return result;
        } catch (AssistantException e) {
            turn.fail(e.code());
            throw e;
        } catch (RuntimeException e) {
            turn.fail(ErrorCode.AI_INTERNAL_ERROR);
            throw e;
        }
    }

    private record ExecutedTool(ToolOutcome outcome, Instant queriedAt) {
    }

    /** The state of one {@link #chat} call. */
    private final class Turn {

        private final String requestId = RequestIdFilter.current();
        private final CitationIds citationIds = new CitationIds();
        private final Instant started = clock.instant();
        private final Instant deadline = started.plus(limits.requestTimeout());
        private final UserContext user;
        private final CustomerContext customer;
        private final ToolContext toolContext;
        private final AssembledPrompt prompt;
        private final List<ExecutedTool> executed = new ArrayList<>();
        /** Calls beyond the cap: reported in the summary, never executed, never audited as tool calls. */
        private final List<ToolOutcome> skipped = new ArrayList<>();
        private String redactedQuestion;
        private int modelCalls;
        private long totalTokens;
        private long inputTokens;
        private long outputTokens;
        private int peakOutputTokens;
        /** Set once the final answer passed validation; the audit's answer and citation data come from here. */
        private AssembledAnswer assembled;

        Turn(UserContext user, CustomerContext customer, AssembledPrompt prompt) {
            this.user = user;
            this.customer = customer;
            this.prompt = prompt;
            this.toolContext = new ToolContext(user, customer);
        }

        /**
         * Runs the loop and returns the model's raw final text (answer-schema JSON, not yet redacted). Never log
         * it or put it in an exception: {@link AnswerAssembler} validates and redacts it.
         */
        String answer(ChatCommand command) {
            redactedQuestion = redact(command.message());
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(prompt.text()));
            messages.addAll(history(command.history()));
            messages.add(UserMessage.from(redactedQuestion));

            List<AssistantTool<?>> tools = registry.toolsFor(customer.accessLevel());
            List<ToolSpecification> specifications = tools.stream().map(AssistantTool::specification).toList();
            Set<String> offered = tools.stream().map(AssistantTool::name).collect(Collectors.toUnmodifiableSet());

            int toolRounds = 0;
            while (true) {
                // Once either cap is reached, one last request without tools forces a text answer (ruling R4).
                boolean offerTools = toolRounds < limits.maxModelRounds()
                        && executed.size() < limits.maxToolCalls();
                AiMessage reply = callModel(messages, offerTools ? specifications : List.of());
                if (!offerTools || !reply.hasToolExecutionRequests()) {
                    // No text is left to the assembler, which reports it as AI_ANSWER_FORMAT_INVALID.
                    return reply.text() == null ? "" : reply.text();
                }
                toolRounds++;
                runTools(reply, messages, offered);
            }
        }

        /** Keeps the newest messages that fit both the message-count and the character limits. */
        private List<ChatMessage> history(List<HistoryMessage> history) {
            for (HistoryMessage message : history) {
                boolean knownRole = USER_ROLE.equals(message.role()) || ASSISTANT_ROLE.equals(message.role());
                if (!knownRole || message.content() == null) {
                    throw new AssistantException(ErrorCode.AI_INPUT_INVALID);
                }
            }
            int maxMessages = limits.historyMaxTurns() * 2;
            Deque<ChatMessage> kept = new ArrayDeque<>();
            int chars = 0;
            for (int i = history.size() - 1; i >= 0 && kept.size() < maxMessages; i--) {
                HistoryMessage message = history.get(i);
                String text = redact(message.content());
                chars += text.codePointCount(0, text.length());
                if (chars > limits.historyMaxChars()) {
                    break;
                }
                kept.addFirst(USER_ROLE.equals(message.role()) ? UserMessage.from(text) : AiMessage.from(text));
            }
            return List.copyOf(kept);
        }

        /** Any round may be the last, so each one carries the schema with the citation ids issued so far. */
        private AiMessage callModel(List<ChatMessage> messages, List<ToolSpecification> specifications) {
            checkDeadline();
            ChatRequest request = ChatRequest.builder().messages(List.copyOf(messages))
                    .toolSpecifications(specifications)
                    .responseFormat(AnswerSchema.responseFormat(citationIds.issued())).build();
            modelCalls++;
            ChatResponse response;
            try {
                response = chatModel.chat(request);
            } catch (RuntimeException e) {
                ErrorCode code = errorMapper.map(e);
                // Type only: provider messages may echo request content.
                log.error("LLM call failed requestId={} code={} error={}", requestId, code, e.getClass().getName());
                throw new AssistantException(code);
            }
            totalTokens += tokens(response.tokenUsage());
            inputTokens += inputTokens(response.tokenUsage());
            outputTokens += outputTokens(response.tokenUsage());
            peakOutputTokens = Math.max(peakOutputTokens, outputTokens(response.tokenUsage()));
            if (totalTokens > limits.tokenBudget()) {
                throw new AssistantException(ErrorCode.AI_TOKEN_BUDGET_EXCEEDED);
            }
            if (response.aiMessage() == null) {
                throw new AssistantException(ErrorCode.AI_LLM_UNAVAILABLE);
            }
            return response.aiMessage();
        }

        /**
         * Executes the requested calls up to the tool-call cap. Calls beyond the cap are not executed but are still
         * echoed with a TOOL_CALL_LIMIT_REACHED result, so every tool call id the model sees has exactly one result.
         */
        private void runTools(AiMessage reply, List<ChatMessage> messages, Set<String> offered) {
            List<ToolExecutionRequest> echoed = new ArrayList<>();
            List<ChatMessage> results = new ArrayList<>();
            for (ToolExecutionRequest request : reply.toolExecutionRequests()) {
                checkDeadline();
                ToolExecutionRequest safe = redactCall(request);
                ToolOutcome outcome;
                if (executed.size() >= limits.maxToolCalls()) {
                    outcome = executor.limitReached(safe, toolContext);
                    skipped.add(outcome);
                } else {
                    outcome = executor.execute(safe, toolContext, offered, citationIds);
                    executed.add(new ExecutedTool(outcome, clock.instant()));
                }
                echoed.add(safe);
                results.add(ToolExecutionResultMessage.from(safe, outcome.payloadForModel()));
            }
            messages.add(AiMessage.builder().text(redact(reply.text())).toolExecutionRequests(echoed).build());
            messages.addAll(results);
        }

        /** Model-written name and arguments are echoed back to the model, so they are redacted like any text. */
        private ToolExecutionRequest redactCall(ToolExecutionRequest request) {
            return ToolExecutionRequest.builder().id(request.id()).name(redact(request.name()))
                    .arguments(redact(request.arguments())).build();
        }

        private void checkDeadline() {
            if (!clock.instant().isBefore(deadline)) {
                throw new AssistantException(ErrorCode.AI_REQUEST_TIMEOUT);
            }
        }

        /** Validates the raw answer and builds the response; the shown (and audited) answer is finalised here. */
        ChatResult result(String rawAnswer) {
            Instant queriedAt = clock.instant();
            List<ToolCallSummary> toolCalls = Stream.concat(executed.stream().map(ExecutedTool::outcome), skipped.stream())
                    .map(o -> new ToolCallSummary(o.toolName(), o.purpose(), o.result().status().name(),
                            o.durationMs()))
                    .toList();
            // Only SUCCESS calls carry a citation id (ToolExecutor).
            Map<String, ExecutedTool> successful = new LinkedHashMap<>();
            boolean marketData = false;
            for (ExecutedTool tool : executed) {
                if (tool.outcome().citationId() == null) {
                    continue;
                }
                successful.put(tool.outcome().citationId(), tool);
                marketData |= registry.find(tool.outcome().toolName()).filter(MarketTool.class::isInstance)
                        .isPresent();
            }
            String disclaimer = marketData ? AnswerAssembler.MARKET_DISCLAIMER : null;
            assembled = answerAssembler.assemble(rawAnswer,
                    successful.values().stream().map(ExecutedTool::outcome).toList(), customer, disclaimer != null);
            List<CitationView> citations = assembled.citationNumbers().entrySet().stream()
                    .map(e -> citation(e.getValue(), successful.get(e.getKey()),
                            assembled.cited().contains(e.getKey())))
                    .toList();
            return new ChatResult(requestId, assembled.blocks(), assembled.answer(), citations, queriedAt,
                    disclaimer, toolCalls);
        }

        /** Tool-provided texts may echo anything, so they go through the same redaction as the answer. */
        private CitationView citation(int id, ExecutedTool tool, boolean cited) {
            ToolOutcome outcome = tool.outcome();
            CitationDetail detail = outcome.result().citation();
            List<Source> sources = outcome.result().sources();
            Source source = sources.isEmpty() ? null : sources.get(0);
            return new CitationView(id, outcome.toolName(),
                    detail == null ? outcome.purpose() : shown(detail.title()),
                    detail == null ? null : shown(detail.target()),
                    detail == null ? null : shown(detail.excerpt()),
                    detail == null ? null : shown(detail.description()),
                    source == null ? null : source.name(), source == null ? null : source.url(),
                    source == null ? null : source.dataTime(), tool.queriedAt(), cited);
        }

        void fail(ErrorCode code) {
            audit(null, "FAILED", code.name());
            log.warn("chat failed requestId={} userId={} code={} durationMs={} modelCalls={} toolCalls={} "
                            + "totalTokens={} inputTokens={} outputTokens={}", requestId, user.userId(), code,
                    elapsedMs(), modelCalls,
                    executed.size(), totalTokens, inputTokens, outputTokens);
        }

        /** {@code answer} is null for a failed question: no answer text and no citation data are audited. */
        void audit(AssembledAnswer answer, String outcome, String errorCode) {
            List<ToolOutcome> outcomes = executed.stream().map(ExecutedTool::outcome).toList();
            auditLogger.record(answer == null
                    ? new AuditEntry(requestId, user, customer, redactedQuestion, null, false, outcome, errorCode,
                            outcomes, Map.of(), List.of(), elapsedMs(), modelCalls, inputTokens,
                            outputTokens, totalTokens, prompt.versions())
                    : new AuditEntry(requestId, user, customer, redactedQuestion, answer.annotatedAnswer(),
                            answer.truncated(), outcome, errorCode, outcomes, answer.citationNumbers(),
                            answer.unverifiedNumbers(), elapsedMs(), modelCalls, inputTokens, outputTokens, totalTokens,
                            prompt.versions()));
        }

        long elapsedMs() {
            return Duration.between(started, clock.instant()).toMillis();
        }

        private String redact(String text) {
            return redactor.redact(text, customer);
        }

        /** Redacted, then the self token becomes plain wording, as in the answer text. */
        private String shown(String text) {
            return text == null ? null
                    : redact(text).replace(PiiRedactor.SELF, CustomerDeidentifier.SUBJECT);
        }
    }

    private static long tokens(TokenUsage usage) {
        return usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount();
    }

    private static long inputTokens(TokenUsage usage) {
        return usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
    }

    private static int outputTokens(TokenUsage usage) {
        return usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
    }
}
