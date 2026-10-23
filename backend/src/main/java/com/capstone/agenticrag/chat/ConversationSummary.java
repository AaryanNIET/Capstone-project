package com.capstone.agenticrag.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rolling summary of the older part of a long conversation (everything before the recent-message window). */
@Entity
@Table(name = "conversation_summaries")
public class ConversationSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "covered_message_count", nullable = false)
    private int coveredMessageCount;

    @Column(name = "last_message_created_at", nullable = false)
    private Instant lastMessageCreatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ConversationSummary() {}

    public ConversationSummary(UUID conversationId, UUID userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    public void update(String summary, int coveredMessageCount, Instant lastMessageCreatedAt) {
        this.summary = summary;
        this.coveredMessageCount = coveredMessageCount;
        this.lastMessageCreatedAt = lastMessageCreatedAt;
        this.updatedAt = Instant.now();
    }

    public UUID getConversationId() { return conversationId; }
    public UUID getUserId() { return userId; }
    public String getSummary() { return summary; }
    public int getCoveredMessageCount() { return coveredMessageCount; }
    public Instant getLastMessageCreatedAt() { return lastMessageCreatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
