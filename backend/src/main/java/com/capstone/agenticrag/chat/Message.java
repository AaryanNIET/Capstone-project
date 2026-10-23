package com.capstone.agenticrag.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

    public enum Role { USER, ASSISTANT }

    public enum Status { COMPLETED, CANCELLED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.COMPLETED;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssistantMode mode;

    @Column(name = "used_documents", nullable = false)
    private boolean usedDocuments;

    @Column(name = "used_web", nullable = false)
    private boolean usedWeb;

    @Column(name = "used_memory", nullable = false)
    private boolean usedMemory;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Message() {}

    public Message(UUID conversationId, UUID userId, Role role, String content, AssistantMode mode) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.mode = mode;
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public AssistantMode getMode() { return mode; }
    public boolean isUsedDocuments() { return usedDocuments; }
    public void setUsedDocuments(boolean v) { this.usedDocuments = v; }
    public boolean isUsedWeb() { return usedWeb; }
    public void setUsedWeb(boolean v) { this.usedWeb = v; }
    public boolean isUsedMemory() { return usedMemory; }
    public void setUsedMemory(boolean v) { this.usedMemory = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Instant getCreatedAt() { return createdAt; }
}
