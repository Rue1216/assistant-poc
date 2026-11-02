package com.example.copilot.aiassistant.domain.customer;

import java.time.LocalDate;
import java.util.List;

/** Identity and scope fields only; never sent to the model. certNo is the national ID. */
public record CustomerRecord(String certNo, String name, String mobile, String email,
                             List<String> accountNumbers, LocalDate birthDate, String employer, String address,
                             String segment, String primaryBranch, String profitBranch, String wealthBranch,
                             String assigneeId, String assigneeBranch, String marketingUnit) {
}
