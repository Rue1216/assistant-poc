package com.example.copilot.aiassistant.domain.customer.c360;

public enum AlertType {
    MEMBER_DOWNGRADE("理財會員降等"),
    LARGE_INFLOW("大額金流流入"),
    IDLE_FUNDS("資金活水"),
    STOP_PROFIT_LOSS("停利停損"),
    OTHER("其他");

    private final String displayName;

    AlertType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
