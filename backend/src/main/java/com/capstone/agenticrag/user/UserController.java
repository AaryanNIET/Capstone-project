package com.capstone.agenticrag.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Profile")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Get the current user's profile")
    public UserDtos.UserResponse profile(@CurrentUser UserPrincipal me) {
        return UserDtos.UserResponse.from(users.get(me.id()));
    }

    @PatchMapping
    @Operation(summary = "Update name or default assistant mode")
    public UserDtos.UserResponse update(@CurrentUser UserPrincipal me, @Valid @RequestBody UserDtos.UpdateProfileRequest req) {
        return UserDtos.UserResponse.from(users.updateProfile(me.id(), req));
    }

    @PostMapping("/password")
    @Operation(summary = "Change password")
    public ResponseEntity<Void> changePassword(@CurrentUser UserPrincipal me,
                                               @Valid @RequestBody UserDtos.ChangePasswordRequest req) {
        users.changePassword(me.id(), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Permanently delete the account and all of its data")
    public ResponseEntity<Void> deleteAccount(@CurrentUser UserPrincipal me, @Valid @RequestBody UserDtos.DeleteAccountRequest req,
                                              HttpServletRequest request) {
        users.deleteAccount(me.id(), req.password());
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
