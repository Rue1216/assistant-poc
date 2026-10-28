package com.example.copilot.aiassistant.port.c360;

public enum FlagType {
    NO_MARKETING("不宜行銷"),
    COMPLAINT("客訴"),
    HIGH_ASSET("高資產"),
    ZONE_CUSTOMER("專區顧客"),
    PROFESSIONAL_INVESTOR("專投");

    private final String displayName;

    FlagType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
