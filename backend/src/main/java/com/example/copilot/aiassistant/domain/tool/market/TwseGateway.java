package com.example.copilot.aiassistant.domain.tool.market;

import java.util.List;
import java.util.Map;

/** Read access to the four TWSE OpenAPI endpoints (03 section 3.1). */
public interface TwseGateway {

    String MI_INDEX = "/exchangeReport/MI_INDEX";
    String STOCK_DAY_ALL = "/exchangeReport/STOCK_DAY_ALL";
    String BWIBBU_ALL = "/exchangeReport/BWIBBU_ALL";
    String COMPANY_ANNOUNCEMENTS = "/opendata/t187ap04_L";

    /** Rows of one endpoint with trimmed keys. */
    List<Map<String, String>> fetch(String path);
}
