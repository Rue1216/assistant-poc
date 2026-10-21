package com.example.copilot.aiassistant.port;

import java.time.LocalDate;

public record CustomerCampaignRecord(String campaignName, LocalDate date, String channel, String response) {
}
