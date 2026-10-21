package com.example.copilot.aiassistant.privacy;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerAccessPort;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.UserContext;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CustomerContextFactory {

    private final CustomerAccessPort accessPort;
    private final CustomerDataPort dataPort;

    public CustomerContextFactory(CustomerAccessPort accessPort, CustomerDataPort dataPort) {
        this.accessPort = accessPort;
        this.dataPort = dataPort;
    }

    /** Empty when the user has no access or the customer does not exist (the two are indistinguishable). */
    public Optional<CustomerContext> create(UserContext user, String customerId) {
        AccessLevel level = accessPort.checkAccess(user, customerId);
        if (level == AccessLevel.NONE) {
            return Optional.empty();
        }
        return dataPort.findCustomer(customerId).map(record -> build(record, level));
    }

    private static CustomerContext build(CustomerRecord record, AccessLevel level) {
        Set<String> self = new LinkedHashSet<>();
        add(self, record.customerId());
        add(self, record.name());
        Set<String> sensitive = new LinkedHashSet<>();
        add(sensitive, record.nationalId());
        add(sensitive, record.mobile());
        add(sensitive, record.email());
        if (record.accountNumbers() != null) {
            record.accountNumbers().forEach(a -> add(sensitive, a));
        }
        return new CustomerContext(record.customerId(), level, self, sensitive);
    }

    private static void add(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }
}
