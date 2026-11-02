package com.example.copilot.aiassistant.interfaces.rest.config.dto;

import com.example.copilot.aiassistant.domain.config.ValidationError;
import java.util.List;

public record ValidationReportView(boolean valid, List<ValidationError> errors) {

    public static ValidationReportView of(List<ValidationError> errors) {
        return new ValidationReportView(errors.isEmpty(), errors);
    }
}
