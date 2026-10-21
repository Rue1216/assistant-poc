package com.example.copilot.mockplatform;

import java.util.Set;

public record MockUser(String username, String displayName, String roleKey, String roleName,
                       Set<String> permissions, String branchCode, Set<String> regionBranchCodes,
                       String marketingUnit) {
}
