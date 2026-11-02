package com.example.copilot.aiassistant.interfaces.rest.feedback;

import com.example.copilot.aiassistant.application.feedback.FeedbackService;
import com.example.copilot.aiassistant.application.feedback.VersionFeedback;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.ConfigKeys;
import com.example.copilot.aiassistant.application.port.CurrentUserPort;
import com.example.copilot.aiassistant.interfaces.rest.feedback.dto.FeedbackRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Thumbs rating on an answer and the per-version statistics. Not rate limited: a click is not a model call. */
@RestController
public class FeedbackController {

    private final FeedbackService service;
    private final CurrentUserPort currentUser;

    public FeedbackController(FeedbackService service, CurrentUserPort currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PutMapping("/api/ai-assistant/chat/{requestId}/feedback")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@PathVariable @Pattern(regexp = "^[0-9a-f]{8}$") String requestId,
            @Valid @RequestBody FeedbackRequest body) {
        service.rate(requestId, currentUser.currentUser().userId(), body.rating());
    }

    @GetMapping("/api/ai-assistant/admin/configs/{configKey}/feedback-stats")
    @PreAuthorize("hasAuthority('AI_CONFIG_READ')")
    public List<VersionFeedback> stats(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey) {
        return service.stats(configKey);
    }
}
