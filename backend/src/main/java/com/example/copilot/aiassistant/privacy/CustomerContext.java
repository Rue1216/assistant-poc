package com.example.copilot.aiassistant.privacy;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;

/**
 * What the de-identifier needs to know about the bound customer. Never log or serialize this record:
 * it holds the real identifying values, so {@link #toString()} only prints the access level.
 */
public record CustomerContext(String customerId, AccessLevel accessLevel,
                              @JsonIgnore Set<String> selfReferences, @JsonIgnore Set<String> sensitiveValues) {

    public CustomerContext {
        selfReferences = Set.copyOf(selfReferences);
        sensitiveValues = Set.copyOf(sensitiveValues);
    }

    @Override
    public String toString() {
        return "CustomerContext[accessLevel=" + accessLevel + "]";
    }
}
