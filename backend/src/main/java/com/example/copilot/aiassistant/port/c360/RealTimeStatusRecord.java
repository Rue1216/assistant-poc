package com.example.copilot.aiassistant.port.c360;

import java.util.List;

public record RealTimeStatusRecord(
        boolean attentionCustomer,
        boolean specialCareCustomer,
        ProfilePhoto profilePhoto,
        List<CustomFlagging> customerFlaggingList,
        KeyAlerts keyAlerts) {}
