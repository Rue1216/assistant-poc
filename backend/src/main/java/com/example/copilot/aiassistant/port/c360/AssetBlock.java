package com.example.copilot.aiassistant.port.c360;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssetBlock(
        @JsonProperty("auMData") AumItems auMData, long customerTotal, long segmentTotal, long topTenTotal) {}
