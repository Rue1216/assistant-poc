package com.example.copilot.aiassistant.config;

import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.config.dto.AdminTime;
import com.example.copilot.aiassistant.config.dto.ApplyRequest;
import com.example.copilot.aiassistant.config.dto.ConfigKeys;
import com.example.copilot.aiassistant.config.dto.ConfigSummaryView;
import com.example.copilot.aiassistant.config.dto.ConfigVersionView;
import com.example.copilot.aiassistant.config.dto.CreateConfigRequest;
import com.example.copilot.aiassistant.config.dto.PublishRequest;
import com.example.copilot.aiassistant.config.dto.SaveDraftRequest;
import com.example.copilot.aiassistant.config.dto.ValidationReportView;
import com.example.copilot.aiassistant.config.dto.VersionHistoryItem;
import com.example.copilot.aiassistant.port.CurrentUserPort;
import com.example.copilot.aiassistant.prompt.ExampleQuestionParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final ConfigService service;
    private final CurrentUserPort currentUser;
    private final ExampleQuestionParser exampleQuestions;

    public AdminConfigController(ConfigService service, CurrentUserPort currentUser,
                                 ExampleQuestionParser exampleQuestions) {
        this.service = service;
        this.currentUser = currentUser;
        this.exampleQuestions = exampleQuestions;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<ConfigSummaryView> list() {
        return service.list().stream().map(ConfigSummaryView::of).toList();
    }

    @PostMapping
    @PreAuthorize(EDIT)
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigVersionView create(@Valid @RequestBody CreateConfigRequest request) {
        return view(service.createKey(request.configKey(), request.configType(), request.name(), actor()));
    }

    @GetMapping("/{configKey}/versions")
    @PreAuthorize(READ)
    public List<VersionHistoryItem> versions(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey) {
        return service.versions(configKey).stream().map(VersionHistoryItem::of).toList();
    }

    @GetMapping("/{configKey}/versions/{version}")
    @PreAuthorize(READ)
    public ConfigVersionView version(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey,
                                     @PathVariable int version) {
        return view(service.version(configKey, version));
    }

    @PostMapping("/{configKey}/draft")
    @PreAuthorize(EDIT)
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigVersionView createDraft(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey) {
        return view(service.createDraft(configKey, actor()));
    }

    @PutMapping("/{configKey}/draft")
    @PreAuthorize(EDIT)
    public ConfigVersionView save(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey,
                                  @Valid @RequestBody SaveDraftRequest request) {
        if (request.content().getBytes(StandardCharsets.UTF_8).length > ConfigValidator.MAX_BYTES) {
            throw invalidInput("content");
        }
        SaveCommand command = new SaveCommand(request.name(), request.content(), request.changeNote(),
                request.saveMode(), request.docVersion());
        return view(service.saveWorkingCopy(configKey, command, actor()));
    }

    @DeleteMapping("/{configKey}/draft")
    @PreAuthorize(EDIT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey,
                        @RequestParam long docVersion) {
        service.discardDraft(configKey, docVersion, actor());
    }

    @PostMapping("/{configKey}/draft/validate")
    @PreAuthorize(EDIT)
    public ValidationReportView validate(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey) {
        return ValidationReportView.of(service.validateWorkingCopy(configKey));
    }

    @PostMapping("/{configKey}/publish")
    @PreAuthorize(PUBLISH)
    public ConfigVersionView publish(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey,
                                     @Valid @RequestBody PublishRequest request) {
        Instant scheduledAt = null;
        if (request.publishMode() == PublishMode.SCHEDULED) {
            scheduledAt = parseScheduledAt(request.scheduledAt());
        }
        PublishCommand command = new PublishCommand(request.publishMode(), scheduledAt, request.docVersion());
        return view(service.publish(configKey, command, actor()));
    }

    @PostMapping("/{configKey}/versions/{version}/apply")
    @PreAuthorize(EDIT)
    public ConfigVersionView apply(@PathVariable @Pattern(regexp = ConfigKeys.PATTERN) String configKey,
                                   @PathVariable int version,
                                   @RequestBody(required = false) ApplyRequest request) {
        ApplyRequest body = request != null ? request : new ApplyRequest(null, null);
        try {
            return view(service.applyVersion(configKey,
                    new ApplyCommand(version, body.workingCopyDocVersion(), body.confirmed()), actor()));
        } catch (AssistantException e) {
            if (e.code() == ErrorCode.AI_CONFIG_OVERWRITE_PENDING) {
                throw new AssistantException(e.code(), withOffsets(e.details()));
            }
            throw e;
        }
    }

    private ConfigVersionView view(AssistantConfig config) {
        List<String> questions = config.getConfigType() == ConfigType.SYSTEM_INSTRUCTION
                ? exampleQuestions.parse(config.getContent()) : List.of();
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

    /** The service reports Instants; the API shows them with the admin +08:00 offset like every other time. */
    private static List<Object> withOffsets(List<Object> details) {
        return details.stream().map(detail -> {
            if (!(detail instanceof Map<?, ?> map)) {
                return detail;
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((k, v) -> converted.put((String) k, v instanceof Instant i ? AdminTime.at(i) : v));
            return (Object) converted;
        }).toList();
    }

    private static AssistantException invalidInput(String field) {
        return new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(Map.of("field", field)));
    }
}
