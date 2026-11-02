package com.example.copilot.aiassistant.domain.tool;

import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;

/** What a tool may use: who asks, which customer the page is bound to, and that customer's data for this question. */
public record ToolContext(UserContext user, CustomerContext customer, TurnCustomerData data) {

    /** Without customer data: for tools that do not read it (market tools) and their tests. */
    public ToolContext(UserContext user, CustomerContext customer) {
        this(user, customer, null);
    }
}
