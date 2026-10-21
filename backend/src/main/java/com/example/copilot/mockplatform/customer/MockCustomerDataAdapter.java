package com.example.copilot.mockplatform.customer;

import com.example.copilot.aiassistant.port.CustomerCampaignRecord;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.HoldingRecord;
import com.example.copilot.aiassistant.port.InteractionRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MockCustomerDataAdapter implements CustomerDataPort {

    private final MockCustomerData data;

    public MockCustomerDataAdapter(MockCustomerData data) {
        this.data = data;
    }

    @Override
    public Optional<CustomerRecord> findCustomer(String customerId) {
        return data.customers().stream().filter(c -> c.customerId().equals(customerId)).findFirst();
    }

    @Override
    public List<HoldingRecord> findHoldings(String customerId) {
        return data.holdings(customerId);
    }

    @Override
    public List<InteractionRecord> findRecentInteractions(String customerId, int limit) {
        return data.interactions(customerId).stream()
                .sorted(Comparator.comparing(InteractionRecord::date).reversed())
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public List<CustomerCampaignRecord> findCampaignResults(String customerId) {
        return data.campaigns(customerId);
    }
}
