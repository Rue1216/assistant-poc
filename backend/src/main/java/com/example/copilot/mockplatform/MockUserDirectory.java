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
            new MockUser("hq.viewer", "總行查詢人員", "HQ_VIEWER", "總行查詢人員", BASE, "HQ", Set.of(), null),
            new MockUser("region.north", "區域查詢人員", "REGION_VIEWER", "區域查詢人員", BASE, "B101",
                    Set.of("B101", "B102"), null),
            new MockUser("branch.mgr", "分行單位主管", "BRANCH_MANAGER", "分行單位主管", BASE, "B101", Set.of(), null),
            new MockUser("fc.supervisor", "理專主管", "FC_SUPERVISOR", "理專主管", BASE, "B101", Set.of(), null),
            new MockUser("fc.officer", "理專經辦", "FC_OFFICER", "理專經辦", BASE, "B101", Set.of(), null),
            new MockUser("ao.supervisor", "AO 主管", "AO_SUPERVISOR", "AO 主管", BASE, "B101", Set.of(), "MU-N1"),
            new MockUser("ao.officer", "AO 經辦", "AO_OFFICER", "AO 經辦", BASE, "B101", Set.of(), "MU-N1"),
            new MockUser("backoffice.admin", "後台管理人員", "BACKOFFICE_ADMIN", "後台管理人員", ADMIN, "HQ", Set.of(), null),
            new MockUser("it.admin", "IT 管理人員", "IT_ADMIN", "IT 管理人員", ADMIN, "HQ", Set.of(), null));

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
