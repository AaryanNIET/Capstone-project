package com.capstone.agenticrag.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.capstone.agenticrag.chat.AssistantMode;

public final class UserDtos {

    /** At least 8 characters with one letter and one digit. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,128}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-128 characters and contain a letter and a digit";

    private UserDtos() {}

    public record UserResponse(UUID id, String name, String email, Role role, boolean memoryEnabled,
                               boolean deleteMemoriesWithConversation, AssistantMode defaultMode, Instant createdAt) {

        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isMemoryEnabled(),
                    u.isDeleteMemoriesWithConversation(), u.getDefaultMode(), u.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 100, message = "Name must be 1-100 characters") String name,
            AssistantMode defaultMode) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {}

    public record DeleteAccountRequest(@NotBlank String password) {}
}
