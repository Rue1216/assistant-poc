package com.example.copilot.aiassistant.port.c360;

import java.util.List;

public record ManagementCard(List<CampaignDetail> campaignList, List<String> recommendedProducts, String insight) {}
