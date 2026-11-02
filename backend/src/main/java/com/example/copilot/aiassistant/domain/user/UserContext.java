package com.example.copilot.aiassistant.domain.user;

import java.util.Set;

public record UserContext(String userId, String displayName, String roleKey, Set<String> permissions) {
}
