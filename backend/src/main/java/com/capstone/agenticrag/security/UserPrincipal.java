package com.capstone.agenticrag.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.capstone.agenticrag.user.Role;
import com.capstone.agenticrag.user.User;

/**
 * Authenticated principal stored in the (JDBC-backed) HTTP session. Holds no password hash after login so the
 * serialized session never contains credentials.
 */
public final class UserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final Role role;
    private final boolean enabled;
    private transient String passwordHash;

    private UserPrincipal(UUID id, String email, Role role, boolean enabled, String passwordHash) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole(), user.isEnabled(), user.getPasswordHash());
    }

    public UUID id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Called by Spring Security after authentication succeeds. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
