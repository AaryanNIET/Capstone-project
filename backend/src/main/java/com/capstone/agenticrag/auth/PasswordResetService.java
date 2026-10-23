package com.capstone.agenticrag.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.agenticrag.admin.AuditService;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.security.SessionTerminator;
import com.capstone.agenticrag.user.User;
import com.capstone.agenticrag.user.UserRepository;
import com.capstone.agenticrag.user.UserService;

/**
 * Password reset by emailed single-use token. Disabled (HTTP 503) unless SMTP is configured, because the token must
 * never be shown in the API response or logs.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final UserService userService;
    private final MailService mail;
    private final AppProperties props;
    private final AuditService audit;
    private final SessionTerminator sessions;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, UserService userService,
                                MailService mail, AppProperties props, AuditService audit, SessionTerminator sessions) {
        this.tokens = tokens;
        this.users = users;
        this.userService = userService;
        this.mail = mail;
        this.props = props;
        this.audit = audit;
        this.sessions = sessions;
    }

    public boolean isEnabled() {
        return mail.isConfigured();
    }

    @Transactional
    public void requestReset(String email) {
        ensureEnabled();
        users.findByEmail(UserService.normalizeEmail(email)).filter(User::isEnabled).ifPresent(user -> {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            tokens.deleteByUserId(user.getId());
            tokens.save(new PasswordResetToken(user.getId(), Texts.sha256(token),
                    Instant.now().plus(props.mail().resetTokenTtl())));
            String link = props.frontendBaseUrl() + "/reset-password?token=" + token;
            mail.send(user.getEmail(), "Reset your Agentic RAG Assistant password",
                    "Hello " + user.getName() + ",\n\nUse the link below to set a new password. It expires in "
                            + props.mail().resetTokenTtl().toMinutes() + " minutes and can be used once.\n\n" + link
                            + "\n\nIf you did not request this, you can ignore this email.");
            audit.record(user.getId(), user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString(), null);
        });
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        ensureEnabled();
        PasswordResetToken stored = tokens.findByTokenHash(Texts.sha256(token))
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                        "This reset link is invalid or has expired"));
        User user = userService.get(stored.getUserId());
        userService.setPassword(user, newPassword);
        stored.markUsed();
        sessions.terminateAll(user.getEmail());
        audit.record(user.getId(), user.getEmail(), "PASSWORD_RESET_COMPLETED", "USER", user.getId().toString(), null);
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw ApiException.unavailable("PASSWORD_RESET_UNAVAILABLE",
                    "Password reset is not available because email delivery is not configured");
        }
    }
}
