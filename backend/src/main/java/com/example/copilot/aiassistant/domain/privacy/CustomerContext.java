package com.example.copilot.aiassistant.domain.privacy;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.customer.CertNo;
import java.util.Set;

/**
 * What the de-identifier needs to know about the bound customer. Never log or serialize this record:
 * it holds the real identifying values, so {@link #toString()} only prints the access level.
 */
public record CustomerContext(CertNo certNo, AccessLevel accessLevel,
                              Set<String> selfReferences, Set<String> sensitiveValues) {

    public CustomerContext {
        selfReferences = Set.copyOf(selfReferences);
        sensitiveValues = Set.copyOf(sensitiveValues);
    }

    @Override
    public String toString() {
        return "CustomerContext[accessLevel=" + accessLevel + "]";
    }
}
