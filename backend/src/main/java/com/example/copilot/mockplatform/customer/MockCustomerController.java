package com.example.copilot.mockplatform.customer;

import com.example.copilot.aiassistant.interfaces.rest.ApiError;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.application.port.CurrentUserPort;
import com.example.copilot.aiassistant.application.port.CustomerDataPort;
import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import com.example.copilot.aiassistant.domain.user.UserContext;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-side customer screens for the demo. FULL shows the fictional name; LIMITED is masked. */
@RestController
@RequestMapping("/api/mock/customers")
public class MockCustomerController {

    public record CustomerSummary(String certNo, String name, String segment, AccessLevel accessLevel) {
    }

    /** certNo and name are masked for LIMITED. */
    public record CustomerDetail(String certNo, AccessLevel accessLevel, String name, String segment) {
    }

    /** Mock of the platform CIP lookup (findCustomerInfo): only the stop-use flag, "Y" or "N". */
    public record CipInfo(String stopUseCustomerDataFlag) {
    }

    private final CurrentUserPort currentUser;
    private final MockCustomerAccessAdapter access;
    private final CustomerDataPort data;
    private final MockCustomerData allCustomers;

    public MockCustomerController(CurrentUserPort currentUser, MockCustomerAccessAdapter access,
                                  CustomerDataPort data, MockCustomerData allCustomers) {
        this.currentUser = currentUser;
        this.access = access;
        this.data = data;
        this.allCustomers = allCustomers;
    }

    /** Scope only: a customer who stopped data use is still listed; the 360 page stops at the CIP check. */
    @GetMapping
    List<CustomerSummary> list() {
        UserContext user = currentUser.currentUser();
        return allCustomers.customers().stream()
                .map(c -> toSummary(c, access.scopeLevel(user, c.certNo())))
                .filter(s -> s.accessLevel() != AccessLevel.NONE)
                .toList();
    }

    @GetMapping("/{certNo}")
    ResponseEntity<?> detail(@PathVariable String certNo) {
        AccessLevel level = access.checkAccess(currentUser.currentUser(), certNo);
        CustomerRecord c = data.findCustomer(certNo).orElse(null);
        if (level == AccessLevel.NONE || c == null) {
            return notFound();
        }
        if (level == AccessLevel.LIMITED) {
            return ResponseEntity.ok(new CustomerDetail(maskCertNo(c.certNo()), level, mask(c.name()), c.segment()));
        }
        return ResponseEntity.ok(new CustomerDetail(c.certNo(), level, c.name(), c.segment()));
    }

    @GetMapping("/{certNo}/360")
    ResponseEntity<?> customer360(@PathVariable String certNo) {
        return fullOnly(certNo, id -> data.findCustomer360(id).orElse(null));
    }

    @GetMapping("/{certNo}/real-time")
    ResponseEntity<?> realTime(@PathVariable String certNo) {
        return fullOnly(certNo, id -> data.findRealTimeStatus(id).orElse(null));
    }

    /** Scope check only: the flag itself is what this endpoint reports. */
    @GetMapping("/{certNo}/cip")
    ResponseEntity<?> cip(@PathVariable String certNo) {
        AccessLevel level = access.scopeLevel(currentUser.currentUser(), certNo);
        if (level == AccessLevel.NONE || data.findCustomer(certNo).isEmpty()) {
            return notFound();
        }
        return ResponseEntity.ok(new CipInfo(allCustomers.stopUseCustomerData(certNo) ? "Y" : "N"));
    }

    private ResponseEntity<?> fullOnly(String certNo, Function<String, Object> loader) {
        AccessLevel level = access.checkAccess(currentUser.currentUser(), certNo);
        if (level == AccessLevel.NONE) {
            return notFound();
        }
        if (level == AccessLevel.LIMITED) {
            return ResponseEntity.status(ErrorCode.AI_FORBIDDEN.status())
                    .body(ApiError.of(ErrorCode.AI_FORBIDDEN, List.of()));
        }
        Object body = loader.apply(certNo);
        return body == null ? notFound() : ResponseEntity.ok(body);
    }

    // This controller is outside the assistant's exception advice, so build the 404 body here.
    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(ErrorCode.AI_CUSTOMER_NOT_FOUND.status())
                .body(ApiError.of(ErrorCode.AI_CUSTOMER_NOT_FOUND, List.of()));
    }

    private static CustomerSummary toSummary(CustomerRecord c, AccessLevel level) {
        String name = level == AccessLevel.LIMITED ? mask(c.name()) : c.name();
        String certNo = level == AccessLevel.LIMITED ? maskCertNo(c.certNo()) : c.certNo();
        return new CustomerSummary(certNo, name, c.segment(), level);
    }

    /** Keeps the first character and the last four; every character between becomes an asterisk. */
    static String maskCertNo(String certNo) {
        if (certNo == null || certNo.length() <= 5) {
            return certNo;
        }
        return certNo.charAt(0) + "*".repeat(certNo.length() - 5) + certNo.substring(certNo.length() - 4);
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
