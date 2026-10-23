package com.capstone.agenticrag.memory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    Optional<Memory> findByIdAndUserId(UUID id, UUID userId);

    Page<Memory> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    Page<Memory> findByUserIdAndCategoryOrderByUpdatedAtDesc(UUID userId, Memory.Category category, Pageable pageable);

    List<Memory> findByUserId(UUID userId);

    List<Memory> findByUserIdAndCategory(UUID userId, Memory.Category category);

    List<Memory> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

    long countByUserId(UUID userId);

    @Query("""
            select m from Memory m where m.userId = :userId and (m.expiresAt is null or m.expiresAt > :now)
            order by m.updatedAt desc
            """)
    List<Memory> findActive(UUID userId, Instant now, Pageable pageable);

    @Modifying
    @Query("delete from Memory m where m.userId = :userId")
    int deleteAllByUserId(UUID userId);

    @Modifying
    @Query("delete from Memory m where m.userId = :userId and m.sourceConversationId = :conversationId")
    int deleteBySourceConversation(UUID userId, UUID conversationId);

    @Modifying
    @Query("update Memory m set m.lastUsedAt = :now where m.id in :ids")
    void markUsed(Collection<UUID> ids, Instant now);
}
