package com.example.copilot.aiassistant.domain.tool.market;

/** A TWSE call failed; {@code errorCode} is MARKET_DATA_TIMEOUT or MARKET_DATA_UNAVAILABLE. */
public class TwseException extends RuntimeException {

    public static final String TIMEOUT = "MARKET_DATA_TIMEOUT";
    public static final String UNAVAILABLE = "MARKET_DATA_UNAVAILABLE";

    private final String errorCode;

    public TwseException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public TwseException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
