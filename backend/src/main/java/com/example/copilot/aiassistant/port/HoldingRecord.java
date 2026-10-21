package com.example.copilot.aiassistant.port;

import java.math.BigDecimal;

/** weight is 0-1; stockCode may be null. */
public record HoldingRecord(String productType, String productName, String stockCode, BigDecimal weight) {
}
