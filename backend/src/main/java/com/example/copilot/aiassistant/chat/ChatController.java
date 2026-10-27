package com.example.copilot.aiassistant.chat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.chat.ChatCommand.HistoryMessage;
import com.example.copilot.aiassistant.chat.dto.ChatRequestBody;
import com.example.copilot.aiassistant.chat.dto.ChatResponseBody;
import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.common.PerUserRateLimiter;
import com.example.copilot.aiassistant.port.CurrentUserPort;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.CustomerContextFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 02 section 2.1. Order: validate, rate limit, resolve customer scope (404), then the tool-calling loop. */
@RestController
public class ChatController {

    private final CurrentUserPort currentUser;
    private final CustomerContextFactory contexts;
    private final ChatOrchestrator orchestrator;
    private final PerUserRateLimiter rateLimiter;
    private final AiAssistantProperties.Limits limits;

    public ChatController(CurrentUserPort currentUser, CustomerContextFactory contexts,
            ChatOrchestrator orchestrator, PerUserRateLimiter rateLimiter, AiAssistantProperties props) {
        this.limits = props.limits();
        this.currentUser = currentUser;
        this.contexts = contexts;
        this.orchestrator = orchestrator;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/ai-assistant/customers/{customerId}/chat")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ChatResponseBody chat(@PathVariable @Pattern(regexp = "^C\\d{3}$") String customerId,
            @Valid @RequestBody ChatRequestBody body) {
        String message = body.message().strip();
        if (message.isEmpty() || message.codePointCount(0, message.length()) > limits.messageMaxChars()) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(Map.of("field", "message")));
        }
        if (body.history() != null && body.history().size() > limits.historyMaxTurns() * 2) {
            throw new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(Map.of("field", "history")));
        }
        UserContext user = currentUser.currentUser();
        if (!rateLimiter.tryAcquire(user.userId())) {
            throw new AssistantException(ErrorCode.AI_RATE_LIMITED);
        }
        CustomerContext customer = contexts.create(user, customerId)
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CUSTOMER_NOT_FOUND));
        List<HistoryMessage> history = body.history() == null ? List.of()
                : body.history().stream().map(h -> new HistoryMessage(h.role(), h.content())).toList();
        return ChatResponseBody.from(orchestrator.chat(new ChatCommand(message, history, body.categoryKey()), user,
                customer));
    }
}
