package com.example.copilot.aiassistant.application.query.chat;

import com.example.copilot.aiassistant.application.config.PublishedConfigCache;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SuggestionQueryService {

    public record SuggestionCategory(String configKey, String name, List<String> questions) {
    }

    private final PublishedConfigCache cache;
    private final ExampleQuestionParser parser;

    public SuggestionQueryService(PublishedConfigCache cache, ExampleQuestionParser parser) {
        this.cache = cache;
        this.parser = parser;
    }

    /** One category per published instruction; not filtered by access level (02 section 2.2).
     *  Categories without example questions are not listed. */
    public List<SuggestionCategory> suggestions() {
        return cache.current().instructions().stream()
                .map(c -> new SuggestionCategory(c.configKey().value(), c.name(), parser.parse(c.content())))
                .filter(c -> !c.questions().isEmpty())
                .toList();
    }
}
