package com.example.copilot.aiassistant.privacy;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerAccessPort;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.UserContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CustomerContextFactory {

    private static final List<String> HONORIFICS = List.of("先生", "小姐", "女士", "太太", "大哥", "大姐", "大姊", "哥", "姐", "姊");

    private final CustomerAccessPort accessPort;
    private final CustomerDataPort dataPort;

    public CustomerContextFactory(CustomerAccessPort accessPort, CustomerDataPort dataPort) {
        this.accessPort = accessPort;
        this.dataPort = dataPort;
    }

    /** Empty when the user has no access or the customer does not exist (the two are indistinguishable). */
    public Optional<CustomerContext> create(UserContext user, String certNo) {
        AccessLevel level = accessPort.checkAccess(user, certNo);
        if (level == AccessLevel.NONE) {
            return Optional.empty();
        }
        return dataPort.findCustomer(certNo).map(record -> build(record, level));
    }

    /**
     * Builds the context for a dataset case at the level the case declares. Only for {@code EvalRunner}: it does not
     * check the user's access to the customer. Empty when the customer does not exist.
     */
    public Optional<CustomerContext> forEvaluation(String certNo, AccessLevel level) {
        return dataPort.findCustomer(certNo).map(record -> build(record, level));
    }

    static CustomerContext build(CustomerRecord record, AccessLevel level) {
        Set<String> self = new LinkedHashSet<>();
        add(self, record.certNo());
        add(self, record.name());
        addNameForms(self, record.name());
        Set<String> sensitive = new LinkedHashSet<>();
        add(sensitive, record.mobile());
        add(sensitive, record.email());
        if (record.accountNumbers() != null) {
            record.accountNumbers().forEach(a -> add(sensitive, a));
        }
        return new CustomerContext(record.certNo(), level, self, sensitive);
    }

    /**
     * Partial forms of the name people actually write (03 section 1.6 rule 1): the given name, the surname with an
     * honorific, and the masked form the platform shows LIMITED users. A single-character surname is assumed.
     * The surname alone is not added (王道, 國王 must survive) and neither is a one-character given name.
     */
    private static void addNameForms(Set<String> target, String name) {
        if (name == null || name.length() < 2) {
            return;
        }
        if (name.length() >= 3) {
            add(target, name.substring(1));
        }
        String surname = name.substring(0, 1);
        HONORIFICS.forEach(h -> add(target, surname + h));
        String last = name.length() == 2 ? "○" : name.substring(name.length() - 1);
        add(target, surname + "○".repeat(name.length() - 2) + last);
    }

    private static void add(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }
}
