package com.example.copilot.aiassistant.chat;

import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.port.CurrentUserPort;
import com.example.copilot.aiassistant.privacy.CustomerContextFactory;
import com.example.copilot.aiassistant.prompt.SuggestionService;
import com.example.copilot.aiassistant.prompt.SuggestionService.SuggestionCategory;
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
    private final SuggestionService suggestions;

    public SuggestionController(CurrentUserPort currentUser, CustomerContextFactory contexts,
            SuggestionService suggestions) {
        this.currentUser = currentUser;
        this.contexts = contexts;
        this.suggestions = suggestions;
    }

    @GetMapping("/api/ai-assistant/customers/{certNo}/suggestions")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public SuggestionsResponse suggestions(@PathVariable @Pattern(regexp = "^[A-Z0-9&]{8,12}$") String certNo) {
        contexts.create(currentUser.currentUser(), certNo)
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_CUSTOMER_NOT_FOUND));
        return new SuggestionsResponse(suggestions.suggestions());
    }
}
