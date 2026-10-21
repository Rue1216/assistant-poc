package com.example.copilot.aiassistant.chat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.audit.AuditEntry;
import com.example.copilot.aiassistant.audit.AuditLogger;
import com.example.copilot.aiassistant.chat.ChatCommand.HistoryMessage;
import com.example.copilot.aiassistant.chat.ChatResult.SourceView;
import com.example.copilot.aiassistant.chat.ChatResult.ToolCallSummary;
import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.common.RequestIdFilter;
import com.example.copilot.aiassistant.llm.LlmErrorMapper;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.prompt.PromptAssembler;
import com.example.copilot.aiassistant.tool.AssistantTool;
import com.example.copilot.aiassistant.tool.Source;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolExecutor;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import com.example.copilot.aiassistant.tool.ToolStatus;
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
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The tool-calling loop (01 section 3). Every text that reaches the model is either the assembled system message,
 * a {@link PiiRedactor}-processed user/assistant text, or a payload built by {@link ToolExecutor}; the customer is
 * never named. The application log carries counters only, never question, answer or tool data (05 section 5).
 */
@Component
public class ChatOrchestrator {

    static final String MARKET_DISCLAIMER = "資訊僅供內部經營參考，不構成投資建議";
    private static final String SELF_WORDING = "本顧客";
    private static final String DECORATION = "[\\s\\u3000>*_\\-（）().。：:※\\u26A0\\uFE0F]*";
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("\\s*([-*_])\\1{2,}\\s*");
    private static final Pattern DISCLAIMER_ONLY = Pattern.compile(
            DECORATION + Pattern.quote(MARKET_DISCLAIMER) + DECORATION);
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final ChatModel chatModel;
    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final PiiRedactor redactor;
    private final PromptAssembler assembler;
    private final AuditLogger auditLogger;
    private final LlmErrorMapper errorMapper;
    private final AiAssistantProperties.Limits limits;
    private final Clock clock;

    public ChatOrchestrator(ChatModel chatModel, ToolRegistry registry, ToolExecutor executor, PiiRedactor redactor,
                            PromptAssembler assembler, AuditLogger auditLogger, LlmErrorMapper errorMapper,
                            AiAssistantProperties props, Clock clock) {
        this.chatModel = chatModel;
        this.registry = registry;
        this.executor = executor;
        this.redactor = redactor;
        this.assembler = assembler;
        this.auditLogger = auditLogger;
        this.errorMapper = errorMapper;
        this.limits = props.limits();
        this.clock = clock;
    }

    /** Answers one question; on failure writes a FAILED audit entry and throws {@link AssistantException}. */
    public ChatResult chat(ChatCommand command, UserContext user, CustomerContext customer) {
        Turn turn = new Turn(user, customer);
        try {
            ChatResult result = turn.result(turn.answer(command));
            turn.audit(result.answer(), "ANSWERED", null);
            log.info("chat answered requestId={} userId={} durationMs={} modelCalls={} toolCalls={} totalTokens={}",
                    turn.requestId, user.userId(), turn.elapsedMs(), turn.modelCalls, turn.executed.size(),
                    turn.totalTokens);
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
        private final Instant started = clock.instant();
        private final Instant deadline = started.plus(limits.requestTimeout());
        private final UserContext user;
        private final CustomerContext customer;
        private final ToolContext toolContext;
        private final List<ExecutedTool> executed = new ArrayList<>();
        /** Calls beyond the cap: reported in the summary, never executed, never audited as tool calls. */
        private final List<ToolOutcome> skipped = new ArrayList<>();
        private String redactedQuestion;
        private int modelCalls;
        private long totalTokens;

        Turn(UserContext user, CustomerContext customer) {
            this.user = user;
            this.customer = customer;
            this.toolContext = new ToolContext(user, customer);
        }

        /** Runs the loop and returns the redacted answer. */
        String answer(ChatCommand command) {
            redactedQuestion = redact(command.message());
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(assembler.systemMessage()));
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
                    String text = reply.text();
                    if (text == null || text.isBlank()) {
                        throw new AssistantException(ErrorCode.AI_LLM_UNAVAILABLE);
                    }
                    return redact(text);
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

        private AiMessage callModel(List<ChatMessage> messages, List<ToolSpecification> specifications) {
            checkDeadline();
            ChatRequest request = ChatRequest.builder().messages(List.copyOf(messages))
                    .toolSpecifications(specifications).build();
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
                    outcome = executor.execute(safe, toolContext, offered);
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

        /** Builds the response from the redacted answer; the shown (and audited) answer is finalised here. */
        ChatResult result(String redactedAnswer) {
            Instant queriedAt = clock.instant();
            List<ToolCallSummary> toolCalls = Stream.concat(executed.stream().map(ExecutedTool::outcome), skipped.stream())
                    .map(o -> new ToolCallSummary(o.toolName(), o.purpose(), o.result().status().name(),
                            o.durationMs()))
                    .toList();
            Map<Source, Instant> sources = new LinkedHashMap<>();
            boolean marketData = false;
            for (ExecutedTool tool : executed) {
                if (tool.outcome().result().status() != ToolStatus.SUCCESS) {
                    continue;
                }
                tool.outcome().result().sources().forEach(s -> sources.putIfAbsent(s, tool.queriedAt()));
                marketData |= registry.find(tool.outcome().toolName()).filter(MarketTool.class::isInstance)
                        .isPresent();
            }
            List<SourceView> sourceViews = sources.entrySet().stream()
                    .map(e -> new SourceView(e.getKey().name(), e.getKey().url(), e.getKey().dataTime(),
                            e.getValue()))
                    .toList();
            String disclaimer = marketData ? MARKET_DISCLAIMER : null;
            String shown = disclaimer == null ? redactedAnswer : stripTrailingDisclaimer(redactedAnswer);
            // Last, after the redaction pass: the token is for the model's input, people read plain wording.
            shown = shown.replace(PiiRedactor.SELF, SELF_WORDING);
            return new ChatResult(requestId, shown, sourceViews, queriedAt, disclaimer, toolCalls);
        }

        void fail(ErrorCode code) {
            audit(null, "FAILED", code.name());
            log.warn("chat failed requestId={} userId={} code={} durationMs={} modelCalls={} toolCalls={} "
                            + "totalTokens={}", requestId, user.userId(), code, elapsedMs(), modelCalls,
                    executed.size(), totalTokens);
        }

        void audit(String answer, String outcome, String errorCode) {
            List<ToolOutcome> outcomes = executed.stream().map(ExecutedTool::outcome).toList();
            auditLogger.record(new AuditEntry(requestId, user, customer, redactedQuestion, answer, outcome,
                    errorCode, outcomes, elapsedMs()));
        }

        long elapsedMs() {
            return Duration.between(started, clock.instant()).toMillis();
        }

        private String redact(String text) {
            return redactor.redact(text, customer);
        }
    }

    /**
     * The UI shows the disclaimer itself, so a last line that only repeats it (possibly with markdown or punctuation
     * around it) is dropped. A line that carries any other content is kept.
     */
    static String stripTrailingDisclaimer(String answer) {
        String[] lines = answer.split("\n", -1);
        int last = lines.length - 1;
        while (last >= 0 && lines[last].isBlank()) {
            last--;
        }
        if (last < 0 || !DISCLAIMER_ONLY.matcher(lines[last]).matches()) {
            return answer;
        }
        List<String> kept = new ArrayList<>(Arrays.asList(lines).subList(0, last));
        trimTrailingBlanks(kept);
        if (!kept.isEmpty() && HORIZONTAL_RULE.matcher(kept.get(kept.size() - 1)).matches()) {
            kept.remove(kept.size() - 1);
            trimTrailingBlanks(kept);
        }
        // Nothing but the disclaimer: keep the answer rather than return an empty one.
        return kept.isEmpty() ? answer : String.join("\n", kept);
    }

    private static void trimTrailingBlanks(List<String> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
    }

    private static long tokens(TokenUsage usage) {
        return usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount();
    }
}
