package com.capstone.agenticrag.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /** Most recent messages first; callers reverse for chronological order. */
    List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    Optional<Message> findByIdAndUserId(UUID id, UUID userId);

    long countByConversationId(UUID conversationId);

    @Query("""
            select m from Message m where m.conversationId = :conversationId and m.createdAt <= :until
            order by m.createdAt asc
            """)
    List<Message> findUpTo(UUID conversationId, Instant until);

    /** Text search over the user's own messages in other conversations. {@code pattern} is a lower-case LIKE pattern. */
    @Query("""
            select m from Message m where m.userId = :userId and m.conversationId <> :excludeConversationId
              and lower(m.content) like :pattern escape '\\'
            order by m.createdAt desc
            """)
    List<Message> searchOtherConversations(UUID userId, UUID excludeConversationId, String pattern, Pageable pageable);

    long countByRole(Message.Role role);
}
