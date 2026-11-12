package com.example.copilot.aiassistant.interfaces.rest.config;

import com.example.copilot.aiassistant.application.config.ApplyCommand;
import com.example.copilot.aiassistant.application.config.ConfigCommandService;
import com.example.copilot.aiassistant.application.config.ConfigQueryService;
import com.example.copilot.aiassistant.application.config.PublishCommand;
import com.example.copilot.aiassistant.application.config.PublishMode;
import com.example.copilot.aiassistant.application.config.SaveCommand;
import com.example.copilot.aiassistant.domain.config.AssistantConfig;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.config.ConfigType;
import com.example.copilot.aiassistant.domain.config.ConfigValidator;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.ApplyRequest;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.ConfigSummaryView;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.ConfigVersionView;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.CreateConfigRequest;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.PublishRequest;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.SaveDraftRequest;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.ValidationReportView;
import com.example.copilot.aiassistant.interfaces.rest.config.dto.VersionHistoryItem;
import com.example.copilot.aiassistant.application.port.CurrentUserPort;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Prompt management API (02 section 3). The platform safety rules are code-only and never appear here. */
@RestController
@RequestMapping("/api/ai-assistant/admin/configs")
public class AdminConfigController {

    private static final String READ = "hasAuthority('AI_CONFIG_READ')";
    private static final String EDIT = "hasAuthority('AI_CONFIG_EDIT')";
    private static final String PUBLISH = "hasAuthority('AI_CONFIG_PUBLISH')";

    private final ConfigCommandService service;
    private final ConfigQueryService queries;
    private final CurrentUserPort currentUser;
    private final ExampleQuestionParser exampleQuestions;

    public AdminConfigController(ConfigCommandService service, ConfigQueryService queries,
                                 CurrentUserPort currentUser, ExampleQuestionParser exampleQuestions) {
        this.service = service;
        this.queries = queries;
        this.currentUser = currentUser;
        this.exampleQuestions = exampleQuestions;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<ConfigSummaryView> list() {
        return queries.list().stream().map(ConfigSummaryView::of).toList();
    }

    @PostMapping
    @PreAuthorize(EDIT)
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigVersionView create(@Valid @RequestBody CreateConfigRequest request) {
        return view(service.createKey(new ConfigKey(request.configKey()), request.configType(), request.name(), actor()));
    }

    @GetMapping("/{configKey}/versions")
    @PreAuthorize(READ)
    public List<VersionHistoryItem> versions(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey) {
        return queries.versions(new ConfigKey(configKey)).stream().map(VersionHistoryItem::of).toList();
    }

    @GetMapping("/{configKey}/versions/{version}")
    @PreAuthorize(READ)
    public ConfigVersionView version(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey,
                                     @PathVariable int version) {
        return view(queries.version(new ConfigKey(configKey), version));
    }

    @PostMapping("/{configKey}/draft")
    @PreAuthorize(EDIT)
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigVersionView createDraft(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey) {
        return view(service.createDraft(new ConfigKey(configKey), actor()));
    }

    @PutMapping("/{configKey}/draft")
    @PreAuthorize(EDIT)
    public ConfigVersionView save(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey,
                                  @Valid @RequestBody SaveDraftRequest request) {
        if (request.content().getBytes(StandardCharsets.UTF_8).length > ConfigValidator.MAX_BYTES) {
            throw invalidInput("content");
        }
        SaveCommand command = new SaveCommand(request.name(), request.content(), request.changeNote(),
                request.saveMode(), request.docVersion());
        return view(service.saveWorkingCopy(new ConfigKey(configKey), command, actor()));
    }

    @DeleteMapping("/{configKey}/draft")
    @PreAuthorize(EDIT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey,
                        @RequestParam long docVersion) {
        service.discardDraft(new ConfigKey(configKey), docVersion, actor());
    }

    @PostMapping("/{configKey}/draft/validate")
    @PreAuthorize(EDIT)
    public ValidationReportView validate(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey) {
        return ValidationReportView.of(service.validateWorkingCopy(new ConfigKey(configKey)));
    }

    @PostMapping("/{configKey}/publish")
    @PreAuthorize(PUBLISH)
    public ConfigVersionView publish(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey,
                                     @Valid @RequestBody PublishRequest request) {
        Instant scheduledAt = null;
        if (request.publishMode() == PublishMode.SCHEDULED) {
            scheduledAt = parseScheduledAt(request.scheduledAt());
        }
        PublishCommand command = new PublishCommand(request.publishMode(), scheduledAt, request.docVersion());
        return view(service.publish(new ConfigKey(configKey), command, actor()));
    }

    @PostMapping("/{configKey}/versions/{version}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public ConfigVersionView apply(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey,
                                   @PathVariable int version,
                                   @RequestBody(required = false) ApplyRequest request) {
        ApplyRequest body = request != null ? request : new ApplyRequest(null, null);
        return view(service.applyVersion(new ConfigKey(configKey),
                new ApplyCommand(version, body.workingCopyDocVersion(), body.confirmed()), actor()));
    }

    private ConfigVersionView view(AssistantConfig config) {
        List<String> questions = config.configType() == ConfigType.SYSTEM_INSTRUCTION
                ? exampleQuestions.parse(config.content()) : List.of();
        return ConfigVersionView.of(config, questions);
    }

    private String actor() {
        return currentUser.currentUser().userId();
    }

    private static Instant parseScheduledAt(String value) {
        if (value == null || value.isBlank()) {
            throw invalidInput("scheduledAt");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw invalidInput("scheduledAt");
        }
    }

    private static AssistantException invalidInput(String field) {
        return new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail(field)));
    }
}
