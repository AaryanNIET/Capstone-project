package com.capstone.agenticrag.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;
import com.capstone.agenticrag.user.UserDtos.UserResponse;
import com.capstone.agenticrag.user.UserService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService auth;
    private final UserService users;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService auth, UserService users, PasswordResetService passwordReset) {
        this.auth = auth;
        this.users = users;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new account and start a session")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req,
                                                 HttpServletRequest request, HttpServletResponse response) {
        users.register(req.name(), req.email(), req.password());
        UserResponse user = auth.login(req.email(), req.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password (sets an HttpOnly session cookie)")
    public UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest request,
                              HttpServletResponse response) {
        return auth.login(req.email(), req.password(), request, response);
    }

    @PostMapping("/logout")
    @Operation(summary = "End the current session")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user")
    public UserResponse me(@CurrentUser UserPrincipal me) {
        return UserResponse.from(users.get(me.id()));
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue a CSRF token (also set in the XSRF-TOKEN cookie)")
    public AuthDtos.CsrfResponse csrf(CsrfToken token) {
        return new AuthDtos.CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request a password-reset email (always 202 to avoid account enumeration)")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody AuthDtos.PasswordResetRequest req) {
        passwordReset.requestReset(req.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Set a new password using a reset token")
    public ResponseEntity<Void> confirmReset(@Valid @RequestBody AuthDtos.PasswordResetConfirmRequest req) {
        passwordReset.confirmReset(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }
}
