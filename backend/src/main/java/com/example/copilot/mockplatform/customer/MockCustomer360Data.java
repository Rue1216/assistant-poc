package com.example.copilot.mockplatform.customer;

import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.RealTimeStatusRecord;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Customer-360 mock data from {@code mock/customer-360.json}: {@code {"<name>": {"customer": ..., "realTime": ...}}}.
 * The platform DTO field names are used as is. A missing or invalid file fails startup.
 */
@Component
public class MockCustomer360Data {

    private static final String RESOURCE = "mock/customer-360.json";

    private record Entry(Customer360Record customer, RealTimeStatusRecord realTime) {
    }

    private final Map<String, Entry> byCertNo = new HashMap<>();

    public MockCustomer360Data(MockCustomerData customers, JsonMapper mapper) {
        Map<String, Entry> byName;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            byName = mapper.readValue(in, new TypeReference<Map<String, Entry>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        byName.forEach((name, entry) -> {
            if (entry.customer() == null || entry.realTime() == null) {
                throw new IllegalStateException(RESOURCE + ": an entry lacks customer or realTime");
            }
            CustomerRecord record = customers.customers().stream().filter(c -> c.name().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(RESOURCE + ": entry matches no mock customer"));
            byCertNo.put(record.certNo(), entry);
        });
    }

    public Optional<Customer360Record> customer360(String certNo) {
        return Optional.ofNullable(byCertNo.get(certNo)).map(Entry::customer);
    }

    public Optional<RealTimeStatusRecord> realTime(String certNo) {
        return Optional.ofNullable(byCertNo.get(certNo)).map(Entry::realTime);
    }
}
