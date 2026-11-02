package com.example.copilot.aiassistant.application.port;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.user.UserContext;


public interface CustomerAccessPort {

    /** Returns NONE when the user may not see the customer or the customer does not exist. */
    AccessLevel checkAccess(UserContext user, String certNo);
}
