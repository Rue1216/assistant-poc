package com.example.copilot.aiassistant.port;

import java.time.LocalDate;
import java.util.List;

public record CustomerRecord(String customerId, String name, String nationalId, String mobile, String email,
                             List<String> accountNumbers, LocalDate birthDate, String employer, String address,
                             String segment, int relationshipYears, long assetAmount, String riskProfile,
                             boolean marketingConsent, String preferredChannel, List<String> tags,
                             String primaryBranch, String profitBranch, String wealthBranch,
                             String assigneeId, String assigneeBranch, String marketingUnit) {
}
