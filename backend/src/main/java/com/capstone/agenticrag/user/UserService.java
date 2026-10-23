package com.capstone.agenticrag.user;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.agenticrag.admin.AuditService;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.document.FileStorageService;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.security.SessionTerminator;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Set<String> adminEmails;
    private final AuditService audit;
    private final FileStorageService storage;
    private final SessionTerminator sessions;

    public UserService(UserRepository users, PasswordEncoder encoder, AppProperties props, AuditService audit,
                       FileStorageService storage, SessionTerminator sessions) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.storage = storage;
        this.sessions = sessions;
        this.adminEmails = props.adminEmails() == null ? Set.of() : props.adminEmails().stream()
                .map(UserService::normalizeEmail).filter(e -> !e.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isConfiguredAdmin(String email) {
        return adminEmails.contains(normalizeEmail(email));
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User(name.trim(), normalized, encoder.encode(rawPassword));
        if (isConfiguredAdmin(normalized)) {
            user.setRole(Role.ADMIN);
        }
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        audit.record(user.getId(), user.getEmail(), "USER_REGISTERED", "USER", user.getId().toString(), null);
        return user;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional
    public User updateProfile(UUID id, UserDtos.UpdateProfileRequest req) {
        User user = get(id);
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw ApiException.badRequest("Name must not be blank");
            }
            user.setName(req.name().trim());
        }
        if (req.defaultMode() != null) {
            user.setDefaultMode(req.defaultMode());
        }
        return user;
    }

    @Transactional
    public void changePassword(UUID id, String currentPassword, String newPassword) {
        User user = get(id);
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                    "Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        audit.record(user.getId(), user.getEmail(), "PASSWORD_CHANGED", "USER", user.getId().toString(), null);
    }

    @Transactional
    public void setPassword(User user, String newPassword) {
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    /** Deletes the account and, through ON DELETE CASCADE, every conversation, document, chunk and memory it owns. */
    @Transactional
    public void deleteAccount(UUID id, String password) {
        User user = get(id);
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password is incorrect");
        }
        users.delete(user);
        users.flush();
        storage.deleteUserFiles(id);
        sessions.terminateAll(user.getEmail());
        audit.record(null, user.getEmail(), "ACCOUNT_DELETED", "USER", id.toString(), null);
    }
}
