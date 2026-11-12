package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.config.ConfigKey;
import com.example.copilot.aiassistant.domain.eval.EvalCaseRecord;
import com.example.copilot.aiassistant.domain.eval.EvalRun;
import com.example.copilot.aiassistant.domain.eval.EvalSummary;
import org.mapstruct.Mapper;

/** Field-for-field mapping between the eval-run aggregate and its stored form. */
@Mapper(componentModel = "spring")
public interface EvalRunDocumentMapper {

    EvalRunDocument toDocument(EvalRun run);

    EvalRun toDomain(EvalRunDocument document);

    EvalRunDocument.CaseDocument toDocument(EvalCaseRecord c);

    EvalRunDocument.SummaryDocument toDocument(EvalSummary s);

    default String map(ConfigKey key) {
        return key == null ? null : key.value();
    }

    default ConfigKey map(String value) {
        return value == null ? null : new ConfigKey(value);
    }
}
