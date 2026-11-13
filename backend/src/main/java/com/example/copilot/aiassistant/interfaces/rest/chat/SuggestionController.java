package com.example.copilot.aiassistant.interfaces.rest.chat;

import com.example.copilot.aiassistant.domain.customer.CertNo;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.application.port.CurrentUserPort;
import com.example.copilot.aiassistant.application.chat.CustomerContextFactory;
import com.example.copilot.aiassistant.application.query.chat.SuggestionQueryService;
import com.example.copilot.aiassistant.application.query.chat.SuggestionQueryService.SuggestionCategory;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SuggestionController {

    public record SuggestionsResponse(List<SuggestionCategory> categories) {
    }

    private final CurrentUserPort currentUser;
    private final CustomerContextFactory contexts;
    private final SuggestionQueryService suggestions;

    public SuggestionController(CurrentUserPort currentUser, CustomerContextFactory contexts,
            SuggestionQueryService suggestions) {
        this.currentUser = currentUser;
        this.contexts = contexts;
        this.suggestions = suggestions;
    }

    @GetMapping("/api/ai-assistant/customers/{certNo}/suggestions")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public SuggestionsResponse suggestions(@PathVariable @Pattern(regexp = CertNo.PATTERN) String certNo) {
        contexts.create(currentUser.currentUser(), new CertNo(certNo))
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CUSTOMER_NOT_FOUND));
        return new SuggestionsResponse(suggestions.suggestions());
    }
}
