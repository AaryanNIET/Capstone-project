package com.capstone.agenticrag.chat;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    Page<Conversation> findByUserIdAndArchivedOrderByUpdatedAtDesc(UUID userId, boolean archived, Pageable pageable);

    /** Searches titles and message bodies of the user's own conversations. {@code pattern} is a lower-case LIKE pattern. */
    @Query("""
            select c from Conversation c
            where c.userId = :userId and c.archived = :archived
              and (lower(c.title) like :pattern escape '\\'
                   or exists (select 1 from Message m where m.conversationId = c.id
                              and lower(m.content) like :pattern escape '\\'))
            order by c.updatedAt desc
            """)
    Page<Conversation> search(UUID userId, boolean archived, String pattern, Pageable pageable);

    long countByUserId(UUID userId);
}
