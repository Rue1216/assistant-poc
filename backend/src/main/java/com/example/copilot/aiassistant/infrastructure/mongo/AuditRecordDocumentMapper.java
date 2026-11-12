package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.audit.AuditRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Command side only: audit records are written, never read back into the domain. */
@Mapper(componentModel = "spring")
public interface AuditRecordDocumentMapper {

    @Mapping(target = "feedback", ignore = true)
    @Mapping(target = "feedbackAt", ignore = true)
    AuditRecordDocument toDocument(AuditRecord record);
}
