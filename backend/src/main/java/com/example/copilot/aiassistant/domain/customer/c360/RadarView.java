package com.example.copilot.aiassistant.domain.customer.c360;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** {@code tagList} is only filled for the consumption-behavior view. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RadarView(
        List<String> labels, List<RadarPoint> customerData, List<RadarPoint> segmentData, List<String> tagList) {}
