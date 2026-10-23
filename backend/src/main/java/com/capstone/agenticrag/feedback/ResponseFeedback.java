package com.capstone.agenticrag.feedback;

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
@Table(name = "response_feedback")
public class ResponseFeedback {

    public enum Rating { UP, DOWN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rating rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ResponseFeedback() {}

    public ResponseFeedback(UUID messageId, UUID userId) {
        this.messageId = messageId;
        this.userId = userId;
    }

    public void update(Rating rating, String comment) {
        this.rating = rating;
        this.comment = comment;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public UUID getUserId() { return userId; }
    public Rating getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getUpdatedAt() { return updatedAt; }
}
