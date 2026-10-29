package com.example.copilot;

import com.example.copilot.mockplatform.MockUser;
import com.example.copilot.mockplatform.MockUserDirectory;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Logs a request in as one of the nine mock accounts. Combine with {@code csrf()} for writes. */
public final class TestUsers {

    private static final MockUserDirectory DIRECTORY = new MockUserDirectory();

    private TestUsers() {
    }

    public static RequestPostProcessor as(String username) {
        MockUser user = DIRECTORY.find(username)
                .orElseThrow(() -> new IllegalArgumentException("unknown mock user " + username));
        List<SimpleGrantedAuthority> authorities = Stream.concat(user.permissions().stream(),
                Stream.of("ROLE_" + user.roleKey())).map(SimpleGrantedAuthority::new).toList();
        return SecurityMockMvcRequestPostProcessors.user(username).authorities(authorities);
    }
}
