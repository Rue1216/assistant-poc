package com.example.copilot.mockplatform;

import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.port.CurrentUserPort;
import com.example.copilot.aiassistant.port.UserContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MockCurrentUserAdapter implements CurrentUserPort {

    private final MockUserDirectory directory;

    public MockCurrentUserAdapter(MockUserDirectory directory) {
        this.directory = directory;
    }

    @Override
    public UserContext currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AssistantException(ErrorCode.AI_UNAUTHENTICATED);
        }
        MockUser user = directory.find(auth.getName())
                .orElseThrow(() -> new AssistantException(ErrorCode.AI_UNAUTHENTICATED));
        return new UserContext(user.username(), user.displayName(), user.roleKey(), user.permissions());
    }
}
