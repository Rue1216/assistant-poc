package com.example.copilot.aiassistant.port.c360;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RadarPoint(int rank, String info, Boolean highlight) {}
