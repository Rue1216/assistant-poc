package com.example.copilot.mockplatform.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.UserContext;
import com.example.copilot.mockplatform.MockUser;
import com.example.copilot.mockplatform.MockUserDirectory;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MockCustomerAccessAdapterTest {

    private static final MockUserDirectory DIRECTORY = new MockUserDirectory();
    private final MockCustomerAccessAdapter adapter = new MockCustomerAccessAdapter(DIRECTORY,
            new MockCustomerDataAdapter(new MockCustomerData()));

    /** docs/spec/02-rest-api.md 1.4; Y = FULL, N = NONE, L = LIMITED (de-identified). */
    private static final String[] USERS = {"hq.viewer", "region.north", "branch.mgr", "fc.supervisor", "fc.officer",
            "ao.supervisor", "ao.officer", "backoffice.admin", "it.admin"};
    private static final String[][] MATRIX = {
            {"C001", "YYYYYYY"}, {"C002", "YYYYYNN"}, {"C003", "YYYYYYN"},
            {"C004", "YYYNNYY"}, {"C005", "YNNNNNN"}, {"C006", "YNNNNYY"}};

    static Stream<Arguments> accessMatrix() {
        return Stream.of(MATRIX).flatMap(row -> Stream.of(USERS).map(user -> {
            int index = java.util.Arrays.asList(USERS).indexOf(user);
            AccessLevel expected = index >= 7 ? AccessLevel.LIMITED
                    : row[1].charAt(index) == 'Y' ? AccessLevel.FULL : AccessLevel.NONE;
            return Arguments.of(row[0], user, expected);
        }));
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @MethodSource
    void accessMatrix(String customerId, String username, AccessLevel expected) {
        assertThat(adapter.checkAccess(contextOf(username), customerId)).isEqualTo(expected);
    }

    @Test
    void matrixCovers54Cells() {
        assertThat(accessMatrix().count()).isEqualTo(54);
    }

    @Test
    void unknownCustomerIsNone() {
        assertThat(adapter.checkAccess(contextOf("hq.viewer"), "C999")).isEqualTo(AccessLevel.NONE);
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
