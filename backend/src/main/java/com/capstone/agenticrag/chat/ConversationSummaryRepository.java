package com.capstone.agenticrag.chat;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, UUID> {

    Optional<ConversationSummary> findByConversationIdAndUserId(UUID conversationId, UUID userId);
}
