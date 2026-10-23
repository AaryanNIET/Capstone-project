package com.capstone.agenticrag.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Service;

import com.capstone.agenticrag.admin.AuditService;
import com.capstone.agenticrag.security.UserPrincipal;
import com.capstone.agenticrag.user.UserDtos.UserResponse;
import com.capstone.agenticrag.user.UserService;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final UserService users;
    private final AuditService audit;
    private final CookieCsrfTokenRepository csrfCookies = CookieCsrfTokenRepository.withHttpOnlyFalse();

    public AuthService(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                       UserService users, AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.users = users;
        this.audit = audit;
    }

    public UserResponse login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        String normalized = UserService.normalizeEmail(email);
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalized, password));
        } catch (AuthenticationException e) {
            log.info("Failed login attempt for a submitted email");
            // Same message for unknown email, wrong password and disabled accounts.
            throw new BadCredentialsException("invalid");
        }
        // Session-fixation protection: never reuse a pre-login session id.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        // Rotate the CSRF token on authentication; the SPA picks up the fresh cookie on its next request.
        csrfCookies.saveToken(null, request, response);

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        audit.record(principal.id(), principal.getUsername(), "LOGIN", "USER", principal.id().toString(), null);
        return UserResponse.from(users.get(principal.id()));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        csrfCookies.saveToken(null, request, response);
    }
}
