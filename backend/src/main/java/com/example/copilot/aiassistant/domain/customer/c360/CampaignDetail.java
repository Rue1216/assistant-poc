package com.example.copilot.aiassistant.domain.customer.c360;

import java.util.List;

public record CampaignDetail(
        String receiptNo,
        String campaignName,
        String campaignDuration,
        String campaignSummary,
        String campaignEnabled,
        List<String> attachment) {}
