package com.example.copilot.aiassistant.port;

public interface CustomerAccessPort {

    /** Returns NONE when the user may not see the customer or the customer does not exist. */
    AccessLevel checkAccess(UserContext user, String certNo);
}
