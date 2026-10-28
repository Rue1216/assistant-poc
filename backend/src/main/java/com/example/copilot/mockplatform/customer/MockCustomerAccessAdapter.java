package com.example.copilot.mockplatform.customer;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.CustomerAccessPort;
import com.example.copilot.aiassistant.port.CustomerDataPort;
import com.example.copilot.aiassistant.port.CustomerRecord;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.mockplatform.MockUser;
import com.example.copilot.mockplatform.MockUserDirectory;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Customer scope rules (docs/spec/02-rest-api.md section 1.3). */
@Component
public class MockCustomerAccessAdapter implements CustomerAccessPort {

    private final MockUserDirectory directory;
    private final CustomerDataPort customers;
    private final MockCustomerData customerData;

    public MockCustomerAccessAdapter(MockUserDirectory directory, CustomerDataPort customers,
                                     MockCustomerData customerData) {
        this.directory = directory;
        this.customers = customers;
        this.customerData = customerData;
    }

    /** A customer who stopped data use is invisible to everyone, whatever the role (platform CIP flag "Y"). */
    @Override
    public AccessLevel checkAccess(UserContext user, String certNo) {
        if (customerData.stopUseCustomerData(certNo)) {
            return AccessLevel.NONE;
        }
        return scopeLevel(user, certNo);
    }

    /** The role-and-scope decision only; ignores the stop-use flag (used by the customer list and /cip). */
    public AccessLevel scopeLevel(UserContext user, String certNo) {
        Optional<MockUser> mockUser = directory.find(user.userId());
        Optional<CustomerRecord> customer = customers.findCustomer(certNo);
        if (mockUser.isEmpty() || customer.isEmpty()) {
            return AccessLevel.NONE;
        }
        return evaluate(mockUser.get(), customer.get());
    }

    private AccessLevel evaluate(MockUser u, CustomerRecord c) {
        String branch = u.branchCode();
        boolean mine = u.username().equals(c.assigneeId());
        boolean unit = u.marketingUnit() != null && u.marketingUnit().equals(c.marketingUnit());
        return switch (u.roleKey()) {
            case "HQ_VIEWER" -> AccessLevel.FULL;
            case "REGION_VIEWER" -> full(inRegion(u, c.primaryBranch(), c.profitBranch(), c.wealthBranch(),
                    c.assigneeBranch()));
            case "BRANCH_MANAGER" -> full(anyEquals(branch, c.primaryBranch(), c.profitBranch(), c.wealthBranch(),
                    c.assigneeBranch()));
            case "FC_SUPERVISOR" -> full(anyEquals(branch, c.primaryBranch(), c.wealthBranch(),
                    c.assigneeBranch()));
            case "FC_OFFICER" -> full(anyEquals(branch, c.primaryBranch(), c.wealthBranch()) || mine);
            case "AO_SUPERVISOR" -> full(anyEquals(branch, c.primaryBranch(), c.profitBranch(),
                    c.assigneeBranch()) || unit);
            case "AO_OFFICER" -> full(anyEquals(branch, c.primaryBranch(), c.profitBranch()) || mine || unit);
            case "BACKOFFICE_ADMIN", "IT_ADMIN" -> AccessLevel.LIMITED;
            default -> AccessLevel.NONE;
        };
    }

    private static AccessLevel full(boolean allowed) {
        return allowed ? AccessLevel.FULL : AccessLevel.NONE;
    }

    private static boolean anyEquals(String branch, String... candidates) {
        for (String candidate : candidates) {
            if (Objects.equals(branch, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inRegion(MockUser u, String... candidates) {
        for (String candidate : candidates) {
            if (u.regionBranchCodes().contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
