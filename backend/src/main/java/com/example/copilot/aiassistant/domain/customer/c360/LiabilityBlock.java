package com.example.copilot.aiassistant.domain.customer.c360;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LiabilityBlock(
        @JsonProperty("luMData") LumItems luMData, long customerTotal, long segmentTotal, long topTenTotal) {}
