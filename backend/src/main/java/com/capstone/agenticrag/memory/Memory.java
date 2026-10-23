package com.capstone.agenticrag.memory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A long-term memory. Its embedding column is maintained through {@link MemoryVectorStore}. */
@Entity
@Table(name = "memories")
public class Memory {

    public enum Category { PREFERENCE, PROJECT, FACT, GOAL, INSTRUCTION }

    /** EXPLICIT: user asked to remember; EXTRACTED: inferred from a user statement; MANUAL: added in settings. */
    public enum Origin { EXPLICIT, EXTRACTED, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origin origin;

    @Column(name = "source_conversation_id")
    private UUID sourceConversationId;

    @Column(name = "source_message_id")
    private UUID sourceMessageId;

    private Float confidence;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected Memory() {}

    public Memory(UUID userId, String content, Category category, Origin origin, UUID sourceConversationId,
                  UUID sourceMessageId, Float confidence) {
        this.userId = userId;
        this.content = content;
        this.category = category;
        this.origin = origin;
        this.sourceConversationId = sourceConversationId;
        this.sourceMessageId = sourceMessageId;
        this.confidence = confidence;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    /** Replaces the content (newer statement wins) and records the new provenance. */
    public void revise(String newContent, Category newCategory, UUID conversationId, UUID messageId, Float newConfidence) {
        this.content = newContent;
        this.category = newCategory;
        if (conversationId != null) {
            this.sourceConversationId = conversationId;
            this.sourceMessageId = messageId;
        }
        if (newConfidence != null && (confidence == null || newConfidence > confidence)) {
            this.confidence = newConfidence;
        }
        this.updatedAt = Instant.now();
    }

    public void edit(String newContent, Category newCategory) {
        this.content = newContent;
        this.category = newCategory;
        this.origin = Origin.MANUAL;
        this.confidence = 1.0f;
        this.updatedAt = Instant.now();
    }

    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getContent() { return content; }
    public Category getCategory() { return category; }
    public Origin getOrigin() { return origin; }
    public UUID getSourceConversationId() { return sourceConversationId; }
    public UUID getSourceMessageId() { return sourceMessageId; }
    public Float getConfidence() { return confidence; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
