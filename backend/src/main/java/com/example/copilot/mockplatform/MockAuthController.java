package com.example.copilot.mockplatform;

import com.example.copilot.aiassistant.interfaces.rest.ApiError;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock/auth")
public class MockAuthController {

    public record LoginRequest(String username, String password) {
    }

    public record MeResponse(String username, String displayName, String roleKey, String roleName,
                             Set<String> permissions) {
        static MeResponse of(MockUser u) {
            return new MeResponse(u.username(), u.displayName(), u.roleKey(), u.roleName(), u.permissions());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final MockUserDirectory directory;
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public MockAuthController(AuthenticationManager authenticationManager, MockUserDirectory directory) {
        this.authenticationManager = authenticationManager;
        this.directory = directory;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (body.username() == null || body.password() == null) {
            return unauthenticated();
        }
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException e) {
            return unauthenticated();
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();   // avoid session fixation
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        return me(auth);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    ResponseEntity<?> me(Authentication auth) {
        return directory.find(auth.getName()).<ResponseEntity<?>>map(u -> ResponseEntity.ok(MeResponse.of(u)))
                .orElseGet(this::unauthenticated);
    }

    private ResponseEntity<ApiError> unauthenticated() {
        return ResponseEntity.status(ErrorCode.AI_UNAUTHENTICATED.status())
                .body(ApiError.of(ErrorCode.AI_UNAUTHENTICATED, List.of()));
    }
}
