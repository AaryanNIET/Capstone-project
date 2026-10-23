package com.capstone.agenticrag.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import com.capstone.agenticrag.feedback.ResponseFeedback;
import com.capstone.agenticrag.source.SourceDto;

public final class ChatDtos {

    public static final int MAX_MESSAGE_CHARS = 8000;

    private ChatDtos() {}

    public record ConversationItem(UUID id, String title, AssistantMode mode, boolean archived, Instant createdAt,
                                      Instant updatedAt) {
        public static ConversationItem from(Conversation c) {
            return new ConversationItem(c.getId(), c.getTitle(), c.getMode(), c.isArchived(), c.getCreatedAt(),
                    c.getUpdatedAt());
        }
    }

    public record ConversationDetail(UUID id, String title, AssistantMode mode, boolean archived, Instant createdAt,
                                     Instant updatedAt, List<MessageDto> messages) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessageDto(UUID id, UUID conversationId, Message.Role role, String content, Message.Status status,
                             AssistantMode mode, boolean usedDocuments, boolean usedWeb, boolean usedMemory,
                             String errorMessage, Instant createdAt, List<SourceDto> sources,
                             ResponseFeedback.Rating feedback) {

        public static MessageDto from(Message m, List<SourceDto> sources, ResponseFeedback.Rating feedback) {
            return new MessageDto(m.getId(), m.getConversationId(), m.getRole(), m.getContent(), m.getStatus(),
                    m.getMode(), m.isUsedDocuments(), m.isUsedWeb(), m.isUsedMemory(), m.getErrorMessage(),
                    m.getCreatedAt(), sources, feedback);
        }
    }

    public record CreateConversationRequest(
            @Size(max = 200, message = "Title must be at most 200 characters") String title,
            AssistantMode mode) {}

    public record UpdateConversationRequest(
            @Size(min = 1, max = 200, message = "Title must be 1-200 characters") String title,
            Boolean archived,
            AssistantMode mode) {}

    /**
     * Send a message. Either {@code content} (a new user message) or {@code regenerate=true} (re-answer the latest
     * user message, replacing the latest assistant reply) must be supplied.
     */
    public record SendMessageRequest(
            @Size(max = MAX_MESSAGE_CHARS, message = "Message must be at most " + MAX_MESSAGE_CHARS + " characters")
            String content,
            AssistantMode mode,
            Boolean regenerate) {

        public boolean isRegenerate() {
            return Boolean.TRUE.equals(regenerate);
        }
    }

    public record ChatResponse(MessageDto userMessage, MessageDto assistantMessage) {}
}
