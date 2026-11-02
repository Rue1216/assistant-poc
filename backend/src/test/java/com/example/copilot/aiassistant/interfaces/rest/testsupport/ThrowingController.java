package com.example.copilot.aiassistant.interfaces.rest.testsupport;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only controller that throws on demand so the exception handler can be exercised. */
@RestController
@RequestMapping("/test-support")
public class ThrowingController {

    public record Body(@NotBlank String message) {}

    @PostMapping("/conflict")
    void conflict() {
        throw new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
    }

    @PostMapping("/details")
    void details() {
        throw new AssistantException(ErrorCode.AI_CONFIG_VALIDATION_FAILED,
                List.of(Map.of("rule", "NOT_EMPTY", "message", "內容不可為空")));
    }

    @PostMapping("/unknown")
    void unknown() {
        throw new RuntimeException("db password=xyz");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody Body body) {
    }

    @GetMapping("/limit")
    void limit(@RequestParam int limit) {
    }

    @GetMapping("/items/{id}")
    void item(@PathVariable @Pattern(regexp = "C\\d{3}") String id) {
    }

    @PostMapping("/denied")
    void denied() {
        throw new AccessDeniedException("nope");
    }
}
