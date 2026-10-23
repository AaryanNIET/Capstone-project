package com.capstone.agenticrag.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.capstone.agenticrag.user.UserDtos;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254) String email,
            @NotBlank(message = "Password is required")
            @Pattern(regexp = UserDtos.PASSWORD_REGEX, message = UserDtos.PASSWORD_MESSAGE) String password) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Size(max = 254) String email,
            @NotBlank(message = "Password is required") @Size(max = 128) String password) {}

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record PasswordResetConfirmRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Pattern(regexp = UserDtos.PASSWORD_REGEX, message = UserDtos.PASSWORD_MESSAGE) String newPassword) {}

    public record CsrfResponse(String headerName, String token) {}
}
