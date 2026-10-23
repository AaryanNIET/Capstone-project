package com.capstone.agenticrag.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.capstone.agenticrag.chat.AssistantMode;

/** Operational record of one agent run: status, timing, iterations and the (truncated) tool calls made. */
@Entity
@Table(name = "agent_executions")
public class AgentExecution {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "assistant_message_id")
    private UUID assistantMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistantMode mode;

    @Column(length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentResult.Status status;

    @Column(nullable = false)
    private int iterations;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", nullable = false, columnDefinition = "jsonb")
    private List<ToolCallRecord> toolCalls;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected AgentExecution() {}

    public AgentExecution(UUID id, UUID userId, UUID conversationId, UUID assistantMessageId, AssistantMode mode,
                          String model, AgentResult result, Instant startedAt) {
        this.id = id;
        this.userId = userId;
        this.conversationId = conversationId;
        this.assistantMessageId = assistantMessageId;
        this.mode = mode;
        this.model = model;
        this.status = result.status();
        this.iterations = result.iterations();
        this.toolCalls = result.toolCalls();
        this.toolCallCount = result.toolCalls().size();
        this.errorMessage = result.errorCode() == null ? null : result.errorCode() + ": " + result.errorMessage();
        this.startedAt = startedAt;
        this.finishedAt = Instant.now();
        this.durationMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getConversationId() { return conversationId; }
    public UUID getAssistantMessageId() { return assistantMessageId; }
    public AssistantMode getMode() { return mode; }
    public String getModel() { return model; }
    public AgentResult.Status getStatus() { return status; }
    public int getIterations() { return iterations; }
    public int getToolCallCount() { return toolCallCount; }
    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getDurationMs() { return durationMs; }
}
