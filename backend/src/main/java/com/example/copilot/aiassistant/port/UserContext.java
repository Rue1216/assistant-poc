package com.example.copilot.aiassistant.port;

import java.util.Set;

public record UserContext(String userId, String displayName, String roleKey, Set<String> permissions) {
}
