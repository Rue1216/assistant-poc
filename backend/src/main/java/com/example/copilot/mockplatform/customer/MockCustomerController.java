package com.example.copilot.mockplatform.customer;

import com.example.copilot.aiassistant.common.ApiError;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CurrentUserPort;
import com.example.copilot.aiassistant.port.CustomerAccessPort;
import com.example.copilot.aiassistant.port.CustomerCampaignRecord;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.HoldingRecord;
import com.example.copilot.aiassistant.port.InteractionRecord;
import com.example.copilot.aiassistant.port.UserContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-side customer screens for the demo. FULL shows the fictional name; LIMITED is masked. */
@RestController
@RequestMapping("/api/mock/customers")
public class MockCustomerController {

    public record CustomerSummary(String customerId, String name, String segment, AccessLevel accessLevel) {
    }

    /** Identifying and detail fields are null (and omitted) for LIMITED. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerDetail(String customerId, AccessLevel accessLevel, String name, String segment,
                                 int relationshipYears, String riskProfile, boolean marketingConsent,
                                 String preferredChannel, List<String> tags,
                                 String nationalId, String mobile, String email, List<String> accountNumbers,
                                 LocalDate birthDate, String employer, String address, Long assetAmount,
                                 List<HoldingRecord> holdings, List<InteractionRecord> interactions,
                                 List<CustomerCampaignRecord> campaigns) {
    }

    private static final int INTERACTION_LIMIT = 10;

    private final CurrentUserPort currentUser;
    private final CustomerAccessPort access;
    private final CustomerDataPort data;
    private final MockCustomerData allCustomers;

    public MockCustomerController(CurrentUserPort currentUser, CustomerAccessPort access, CustomerDataPort data,
                                  MockCustomerData allCustomers) {
        this.currentUser = currentUser;
        this.access = access;
        this.data = data;
        this.allCustomers = allCustomers;
    }

    @GetMapping
    List<CustomerSummary> list() {
        UserContext user = currentUser.currentUser();
        return allCustomers.customers().stream()
                .map(c -> toSummary(c, access.checkAccess(user, c.customerId())))
                .filter(s -> s.accessLevel() != AccessLevel.NONE)
                .toList();
    }

    @GetMapping("/{customerId}")
    ResponseEntity<?> detail(@PathVariable String customerId) {
        UserContext user = currentUser.currentUser();
        AccessLevel level = access.checkAccess(user, customerId);
        CustomerRecord c = data.findCustomer(customerId).orElse(null);
        if (level == AccessLevel.NONE || c == null) {
            // This controller is outside the assistant's exception advice, so build the 404 body here.
            return ResponseEntity.status(ErrorCode.AI_CUSTOMER_NOT_FOUND.status())
                    .body(ApiError.of(ErrorCode.AI_CUSTOMER_NOT_FOUND, List.of()));
        }
        if (level == AccessLevel.LIMITED) {
            return ResponseEntity.ok(new CustomerDetail(c.customerId(), level, mask(c.name()), c.segment(),
                    c.relationshipYears(), c.riskProfile(), c.marketingConsent(), c.preferredChannel(), c.tags(),
                    null, null, null, null, null, null, null, null, null, null, null));
        }
        return ResponseEntity.ok(new CustomerDetail(c.customerId(), level, c.name(), c.segment(), c.relationshipYears(),
                c.riskProfile(), c.marketingConsent(), c.preferredChannel(), c.tags(),
                c.nationalId(), c.mobile(), c.email(), c.accountNumbers(), c.birthDate(), c.employer(),
                c.address(), c.assetAmount(), data.findHoldings(customerId),
                data.findRecentInteractions(customerId, INTERACTION_LIMIT), data.findCampaignResults(customerId)));
    }

    private static CustomerSummary toSummary(CustomerRecord c, AccessLevel level) {
        String name = level == AccessLevel.LIMITED ? mask(c.name()) : c.name();
        return new CustomerSummary(c.customerId(), name, c.segment(), level);
    }

    /** Keeps the first and last character; the middle becomes a circle (two-character names: first + circle). */
    static String mask(String name) {
        if (name == null || name.length() < 2) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "○";
        }
        return name.charAt(0) + "○".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
