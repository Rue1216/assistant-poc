package com.example.copilot.aiassistant.domain.customer.c360;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The platform spells two wire names "segement" and "financialCounsultant". */
public record ProfileCard(
        String name,
        String age,
        @JsonProperty("segement") String segment,
        String primaryBranch,
        @JsonProperty("financialCounsultant") String financialConsultant) {}
