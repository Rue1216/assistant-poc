package com.example.copilot.mockplatform;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The nine POC accounts (docs/spec/02-rest-api.md section 1.2). */
@Component
public class MockUserDirectory {

    public static final String CUSTOMER_360_VIEW = "CUSTOMER_360_VIEW";
    public static final String AI_ASSISTANT_USE = "AI_ASSISTANT_USE";
    public static final String AI_CONFIG_READ = "AI_CONFIG_READ";
    public static final String AI_CONFIG_EDIT = "AI_CONFIG_EDIT";
    public static final String AI_CONFIG_PUBLISH = "AI_CONFIG_PUBLISH";

    private static final Set<String> BASE = Set.of(CUSTOMER_360_VIEW, AI_ASSISTANT_USE);
    private static final Set<String> ADMIN = withExtra(AI_CONFIG_READ, AI_CONFIG_EDIT, AI_CONFIG_PUBLISH);

    private final List<MockUser> users = List.of(
            new MockUser("ICRM00047", "總行查詢人員", "ANALYST", "總行查詢人員", BASE, "HQ", Set.of(), null, MockScope.ALL),
            new MockUser("ICRM00007", "區域查詢人員", "ANALYST", "區域查詢人員", BASE, "B101",
                    Set.of("B101", "B102"), null, MockScope.REGION),
            new MockUser("ICRM00018", "分行單位主管", "MANAGER", "分行單位主管", BASE, "B101", Set.of(), null,
                    MockScope.BRANCH),
            new MockUser("ICRM00024", "理專主管", "MANAGER", "理專主管", BASE, "B101", Set.of(), null,
                    MockScope.BRANCH_WEALTH),
            new MockUser("ICRM00028", "理專經辦", "RM", "理專經辦", BASE, "B101", Set.of(), null,
                    MockScope.ASSIGNEE_WEALTH),
            new MockUser("ICRM00036", "AO主管", "MANAGER", "AO主管", BASE, "B101", Set.of(), "MU-N1",
                    MockScope.MARKETING_UNIT),
            new MockUser("ICRM00040", "AO經辦", "AO", "AO經辦", BASE, "B101", Set.of(), "MU-N1",
                    MockScope.ASSIGNEE_MARKETING),
            new MockUser("EEE11111", "後台管理人員", "ADMIN", "後台管理人員", ADMIN, "HQ", Set.of(), null, MockScope.ALL),
            new MockUser("EEE22222", "IT 管理人員", "ADMIN", "IT 管理人員", ADMIN, "HQ", Set.of(), null, MockScope.ALL));

    private static Set<String> withExtra(String... extra) {
        Set<String> all = new LinkedHashSet<>(BASE);
        all.addAll(List.of(extra));
        return Set.copyOf(all);
    }

    public Optional<MockUser> find(String username) {
        return users.stream().filter(u -> u.username().equals(username)).findFirst();
    }

    public List<MockUser> all() {
        return users;
    }
}
