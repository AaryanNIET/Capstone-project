package com.capstone.agenticrag.memory;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MemoryDtos {

    private MemoryDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MemoryResponse(UUID id, String content, Memory.Category category, Memory.Origin origin,
                                 UUID sourceConversationId, UUID sourceMessageId, Float confidence, Instant expiresAt,
                                 Instant createdAt, Instant updatedAt, Instant lastUsedAt) {

        public static MemoryResponse from(Memory m) {
            return new MemoryResponse(m.getId(), m.getContent(), m.getCategory(), m.getOrigin(),
                    m.getSourceConversationId(), m.getSourceMessageId(), m.getConfidence(), m.getExpiresAt(),
                    m.getCreatedAt(), m.getUpdatedAt(), m.getLastUsedAt());
        }
    }

    public record CreateMemoryRequest(
            @NotBlank @Size(min = MemoryPolicy.MIN_LENGTH, max = MemoryPolicy.MAX_LENGTH) String content,
            @NotNull Memory.Category category) {}

    public record UpdateMemoryRequest(
            @Size(min = MemoryPolicy.MIN_LENGTH, max = MemoryPolicy.MAX_LENGTH) String content,
            Memory.Category category) {}

    public record MemorySettings(boolean enabled, boolean deleteWithConversation) {}

    public record UpdateMemorySettingsRequest(Boolean enabled, Boolean deleteWithConversation) {}

    public record ClearResult(int deleted) {}
}
