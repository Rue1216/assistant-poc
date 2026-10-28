package com.example.copilot.mockplatform.customer;

import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.RealTimeStatusRecord;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MockCustomerDataAdapter implements CustomerDataPort {

    private final MockCustomerData data;
    private final MockCustomer360Data data360;

    public MockCustomerDataAdapter(MockCustomerData data, MockCustomer360Data data360) {
        this.data = data;
        this.data360 = data360;
    }

    @Override
    public Optional<CustomerRecord> findCustomer(String certNo) {
        return data.customers().stream().filter(c -> c.certNo().equals(certNo)).findFirst();
    }

    @Override
    public Optional<Customer360Record> findCustomer360(String certNo) {
        return data360.customer360(certNo);
    }

    @Override
    public Optional<RealTimeStatusRecord> findRealTimeStatus(String certNo) {
        return data360.realTime(certNo);
    }
}
