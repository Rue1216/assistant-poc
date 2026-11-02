package com.example.copilot.mockplatform.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.user.UserContext;
import com.example.copilot.mockplatform.MockUser;
import com.example.copilot.mockplatform.MockUserDirectory;
import com.example.copilot.testsupport.MockCustomerPorts;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MockCustomerAccessAdapterTest {

    private static final MockUserDirectory DIRECTORY = new MockUserDirectory();
    private static final MockCustomerData CUSTOMERS = new MockCustomerData();
    private final MockCustomerAccessAdapter adapter = new MockCustomerAccessAdapter(DIRECTORY,
            new MockCustomerDataAdapter(CUSTOMERS, MockCustomerPorts.data360(CUSTOMERS)), CUSTOMERS);

    /** docs/spec/02-rest-api.md 1.4; Y = FULL, N = NONE, admins are FULL for all. */
    private static final String[] USERS = {"ICRM00047", "ICRM00007", "ICRM00018", "ICRM00024", "ICRM00028",
            "ICRM00036", "ICRM00040", "EEE11111", "EEE22222"};
    private static final String[][] MATRIX = {
            {TestCustomers.RETIREE, "YYYYYYY"}, {TestCustomers.COMPLAINT, "YYYYYNN"}, {TestCustomers.ATTENTION, "YYYYYYN"},
            {TestCustomers.NO_MARKETING, "YYYNNYY"}, {TestCustomers.HIGH_ASSET, "YNNNNNN"}, {TestCustomers.SPECIAL_CARE, "YNNNNYY"}};
    /** The back-office and IT admins (last two accounts) see every customer in full. */
    private static final int FIRST_ADMIN = 7;

    static Stream<Arguments> accessMatrix() {
        return Stream.of(MATRIX).flatMap(row -> Stream.of(USERS).map(user -> {
            int index = java.util.Arrays.asList(USERS).indexOf(user);
            AccessLevel expected = index >= FIRST_ADMIN ? AccessLevel.FULL
                    : row[1].charAt(index) == 'Y' ? AccessLevel.FULL : AccessLevel.NONE;
            return Arguments.of(row[0], user, expected);
        }));
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @MethodSource
    void accessMatrix(String certNo, String username, AccessLevel expected) {
        assertThat(adapter.checkAccess(contextOf(username), certNo)).isEqualTo(expected);
    }

    @Test
    void matrixCovers54Cells() {
        assertThat(accessMatrix().count()).isEqualTo(54);
    }

    @Test
    void unknownCustomerIsNone() {
        assertThat(adapter.checkAccess(contextOf("ICRM00047"), TestCustomers.UNKNOWN)).isEqualTo(AccessLevel.NONE);
    }

    @Test
    void stopUseIsNoneForEveryone() {
        for (String username : USERS) {
            assertThat(adapter.checkAccess(contextOf(username), TestCustomers.STOP_USE))
                    .as(username).isEqualTo(AccessLevel.NONE);
        }
        // The scope decision alone ignores the flag: the assignee (ICRM00028) would have FULL.
        assertThat(adapter.scopeLevel(contextOf("ICRM00028"), TestCustomers.STOP_USE)).isEqualTo(AccessLevel.FULL);
    }

    @Test
    void accessLevelCovers() {
        assertThat(AccessLevel.FULL.covers(AccessLevel.LIMITED)).isTrue();
        assertThat(AccessLevel.FULL.covers(AccessLevel.FULL)).isTrue();
        assertThat(AccessLevel.LIMITED.covers(AccessLevel.FULL)).isFalse();
        assertThat(AccessLevel.NONE.covers(AccessLevel.LIMITED)).isFalse();
    }

    private static UserContext contextOf(String username) {
        MockUser u = DIRECTORY.find(username).orElseThrow();
        return new UserContext(u.username(), u.displayName(), u.roleKey(), u.permissions());
    }
}
