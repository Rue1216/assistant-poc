package com.example.copilot.aiassistant.domain.customer.c360;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberRankData(
        String memberRankCode,
        String memberRankName,
        String progressPercent,
        @JsonProperty("segementProgressPercent") String segmentProgressPercent,
        String upgradeOrDownGradeAlert,
        String customerAum,
        String segmentAverageAum) {}
