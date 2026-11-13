package com.example.copilot.aiassistant.application.query.chat;

import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.application.chat.CustomerContextFactory;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import com.example.copilot.aiassistant.domain.user.UserContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SuggestionQueryService {

    public record SuggestionCategory(String configKey, String name, List<String> questions) {
    }

    private final PublishedConfigCache cache;
    private final ExampleQuestionParser parser;
    private final CustomerContextFactory contexts;

    public SuggestionQueryService(PublishedConfigCache cache, ExampleQuestionParser parser,
                                  CustomerContextFactory contexts) {
        this.cache = cache;
        this.parser = parser;
        this.contexts = contexts;
    }

    /** One category per published instruction; not filtered by access level (02 section 2.2).
     *  Categories without example questions are not listed. The user must have access to the customer
     *  ({@code AI_CUSTOMER_NOT_FOUND} otherwise, the same as chat). */
    public List<SuggestionCategory> suggestions(CertNo certNo, UserContext user) {
        contexts.create(user, certNo).orElseThrow(() -> new AssistantException(ErrorCode.AI_CUSTOMER_NOT_FOUND));
        return cache.current().instructions().stream()
                .map(c -> new SuggestionCategory(c.configKey().value(), c.name(), parser.parse(c.content())))
                .filter(c -> !c.questions().isEmpty())
                .toList();
    }
}
