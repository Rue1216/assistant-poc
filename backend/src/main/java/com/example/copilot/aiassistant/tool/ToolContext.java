package com.example.copilot.aiassistant.tool;

import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.aiassistant.privacy.CustomerContext;

public record ToolContext(UserContext user, CustomerContext customer) {
}
