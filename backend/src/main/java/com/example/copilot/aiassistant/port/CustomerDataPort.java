package com.example.copilot.aiassistant.port;

import java.util.List;
import java.util.Optional;

public interface CustomerDataPort {

    Optional<CustomerRecord> findCustomer(String customerId);

    List<HoldingRecord> findHoldings(String customerId);

    /** Newest first. */
    List<InteractionRecord> findRecentInteractions(String customerId, int limit);

    List<CustomerCampaignRecord> findCampaignResults(String customerId);
}
