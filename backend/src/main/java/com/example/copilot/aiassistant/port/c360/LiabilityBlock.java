package com.example.copilot.aiassistant.port.c360;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LiabilityBlock(
        @JsonProperty("luMData") LumItems luMData, long customerTotal, long segmentTotal, long topTenTotal) {}
