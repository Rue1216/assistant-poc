package com.example.copilot.aiassistant.interfaces.rest.evaluation;

import com.example.copilot.aiassistant.application.command.evaluation.EvalRunCommandUseCase;
import com.example.copilot.aiassistant.application.query.evaluation.EvalRunQueryService;
import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.eval.EvalDataset;
import com.example.copilot.aiassistant.interfaces.rest.evaluation.dto.CurrentEvaluationView;
import com.example.copilot.aiassistant.interfaces.rest.evaluation.dto.EvalCasesView;
import com.example.copilot.aiassistant.interfaces.rest.evaluation.dto.EvalRunSummaryView;
import com.example.copilot.aiassistant.interfaces.rest.evaluation.dto.EvalRunView;
import com.example.copilot.aiassistant.interfaces.rest.evaluation.dto.StartEvaluationRequest;
import com.example.copilot.aiassistant.interfaces.rest.evaluation.dto.StartEvaluationResponse;
import com.example.copilot.aiassistant.application.port.CurrentUserPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Prompt evaluation API (prompt-evaluation spec 6). The judge prompt is code-only and never appears here. */
@RestController
@RequestMapping("/api/ai-assistant/admin")
public class EvalController {

    private static final String READ = "hasAuthority('AI_CONFIG_READ')";
    private static final String EDIT = "hasAuthority('AI_CONFIG_EDIT')";

    private final EvalRunCommandUseCase commands;
    private final EvalRunQueryService queries;
    private final EvalDataset dataset;
    private final CurrentUserPort currentUser;

    public EvalController(EvalRunCommandUseCase commands, EvalRunQueryService queries,
                          EvalDataset dataset, CurrentUserPort currentUser) {
        this.commands = commands;
        this.queries = queries;
        this.dataset = dataset;
        this.currentUser = currentUser;
    }

    @PostMapping("/configs/{configKey}/evaluations")
    @PreAuthorize(EDIT)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartEvaluationResponse start(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey,
                                         @Valid @RequestBody StartEvaluationRequest request) {
        return StartEvaluationResponse.of(
                commands.start(new ConfigKey(configKey), request.docVersion(), currentUser.currentUser().userId()));
    }

    @GetMapping("/configs/{configKey}/evaluations")
    @PreAuthorize(READ)
    public List<EvalRunSummaryView> recent(@PathVariable @Pattern(regexp = ConfigKey.PATTERN) String configKey) {
        return queries.recent(new ConfigKey(configKey)).stream().map(r -> EvalRunSummaryView.of(r, dataset.hash())).toList();
    }

    @GetMapping("/evaluations/current")
    @PreAuthorize(READ)
    public ResponseEntity<CurrentEvaluationView> current() {
        return queries.current().map(r -> ResponseEntity.ok(CurrentEvaluationView.of(r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/evaluations/{runId}")
    @PreAuthorize(READ)
    public EvalRunView run(@PathVariable @Pattern(regexp = "^[0-9a-f]{24}$") String runId) {
        return EvalRunView.of(queries.get(runId), dataset.hash());
    }

    @GetMapping("/evaluation-cases")
    @PreAuthorize(READ)
    public EvalCasesView cases() {
        return new EvalCasesView(dataset.hash(), dataset.cases());
    }
}
