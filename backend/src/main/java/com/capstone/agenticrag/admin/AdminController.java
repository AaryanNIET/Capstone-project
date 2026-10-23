package com.capstone.agenticrag.admin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.agent.AgentExecution;
import com.capstone.agenticrag.agent.AgentExecutionRepository;
import com.capstone.agenticrag.agent.AgentResult;
import com.capstone.agenticrag.chat.ActiveRunRegistry;
import com.capstone.agenticrag.chat.ConversationRepository;
import com.capstone.agenticrag.chat.Message;
import com.capstone.agenticrag.chat.MessageRepository;
import com.capstone.agenticrag.common.PageResponse;
import com.capstone.agenticrag.document.DocumentRepository;
import com.capstone.agenticrag.document.UserDocument;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.feedback.ResponseFeedback;
import com.capstone.agenticrag.feedback.ResponseFeedbackRepository;
import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.SessionTerminator;
import com.capstone.agenticrag.security.UserPrincipal;
import com.capstone.agenticrag.user.Role;
import com.capstone.agenticrag.user.User;
import com.capstone.agenticrag.user.UserRepository;

/**
 * Operational views for administrators. Exposes counts, execution metadata and the audit log, never message content,
 * documents or memories of other users. Access is restricted to ROLE_ADMIN in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    public record Stats(long users, long conversations, long userMessages, long assistantMessages,
                        long documentsReady, long documentsProcessing, long documentsFailed, long executionsCompleted,
                        long executionsFailed, long executionsTimedOut, long executionsCancelled,
                        long executionsLast24h, Double averageDurationMsLast24h, long feedbackUp, long feedbackDown,
                        int activeRuns) {}

    public record AdminUser(UUID id, String name, String email, Role role, boolean enabled, Instant createdAt) {
        static AdminUser from(User u) {
            return new AdminUser(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isEnabled(), u.getCreatedAt());
        }
    }

    public record UpdateUserRequest(Boolean enabled, Role role) {}

    public record ExecutionItem(UUID id, UUID userId, UUID conversationId, String mode, String model,
                                AgentResult.Status status, int iterations, int toolCallCount, String errorMessage,
                                Instant startedAt, Long durationMs) {
        static ExecutionItem from(AgentExecution e) {
            return new ExecutionItem(e.getId(), e.getUserId(), e.getConversationId(), e.getMode().name(), e.getModel(),
                    e.getStatus(), e.getIterations(), e.getToolCallCount(), e.getErrorMessage(), e.getStartedAt(),
                    e.getDurationMs());
        }
    }

    public record AuditItem(UUID id, UUID actorId, String actorEmail, String action, String targetType,
                            String targetId, String details, Instant createdAt) {
        static AuditItem from(AuditLog a) {
            return new AuditItem(a.getId(), a.getActorId(), a.getActorEmail(), a.getAction(), a.getTargetType(),
                    a.getTargetId(), a.getDetails(), a.getCreatedAt());
        }
    }

    private final UserRepository users;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final DocumentRepository documents;
    private final AgentExecutionRepository executions;
    private final ResponseFeedbackRepository feedback;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final SessionTerminator sessions;
    private final ActiveRunRegistry runs;

    public AdminController(UserRepository users, ConversationRepository conversations, MessageRepository messages,
                           DocumentRepository documents, AgentExecutionRepository executions,
                           ResponseFeedbackRepository feedback, AuditLogRepository auditLogs, AuditService audit,
                           SessionTerminator sessions, ActiveRunRegistry runs) {
        this.users = users;
        this.conversations = conversations;
        this.messages = messages;
        this.documents = documents;
        this.executions = executions;
        this.feedback = feedback;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.sessions = sessions;
        this.runs = runs;
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    @Operation(summary = "Usage and health counters")
    public Stats stats() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return new Stats(users.count(), conversations.count(), messages.countByRole(Message.Role.USER),
                messages.countByRole(Message.Role.ASSISTANT), documents.countByStatus(UserDocument.Status.READY),
                documents.countByStatus(UserDocument.Status.PROCESSING) + documents.countByStatus(UserDocument.Status.UPLOADED),
                documents.countByStatus(UserDocument.Status.FAILED), executions.countByStatus(AgentResult.Status.COMPLETED),
                executions.countByStatus(AgentResult.Status.FAILED), executions.countByStatus(AgentResult.Status.TIMED_OUT),
                executions.countByStatus(AgentResult.Status.CANCELLED), executions.countByStartedAtAfter(since),
                executions.averageDurationSince(since), feedback.countByRating(ResponseFeedback.Rating.UP),
                feedback.countByRating(ResponseFeedback.Rating.DOWN), runs.activeCount());
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    @Operation(summary = "List user accounts")
    public PageResponse<AdminUser> users(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(users.findAll(PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"))), AdminUser::from);
    }

    @PatchMapping("/users/{id}")
    @Transactional
    @Operation(summary = "Enable or disable an account, or change its role. Disabling ends the user's sessions.")
    public AdminUser updateUser(@CurrentUser UserPrincipal me, @PathVariable UUID id,
                                @RequestBody UpdateUserRequest req) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getId().equals(me.id()) && (Boolean.FALSE.equals(req.enabled()) || req.role() == Role.USER)) {
            throw ApiException.badRequest("You cannot disable or demote your own account");
        }
        if (req.role() != null && req.role() != user.getRole()) {
            if (user.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
                throw ApiException.badRequest("At least one administrator must remain");
            }
            user.setRole(req.role());
            audit.record(me.id(), me.getUsername(), "ADMIN_ROLE_CHANGED", "USER", id.toString(), "role=" + req.role());
        }
        if (req.enabled() != null && req.enabled() != user.isEnabled()) {
            user.setEnabled(req.enabled());
            audit.record(me.id(), me.getUsername(), req.enabled() ? "ADMIN_USER_ENABLED" : "ADMIN_USER_DISABLED",
                    "USER", id.toString(), null);
        }
        if (!user.isEnabled() || req.role() != null) {
            // Sessions hold the role and enabled flag, so make the user sign in again to pick up the change.
            sessions.terminateAll(user.getEmail());
        }
        return AdminUser.from(user);
    }

    @GetMapping("/executions")
    @Transactional(readOnly = true)
    @Operation(summary = "Recent agent executions (metadata only)")
    public PageResponse<ExecutionItem> executions(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(executions.findAllByOrderByStartedAtDesc(
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200))), ExecutionItem::from);
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    @Operation(summary = "Security and administration audit log")
    public PageResponse<AuditItem> auditLogs(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(auditLogs.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200))), AuditItem::from);
    }
}
