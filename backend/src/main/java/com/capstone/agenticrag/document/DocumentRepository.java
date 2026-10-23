package com.capstone.agenticrag.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<UserDocument, UUID> {

    Optional<UserDocument> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserDocument> findByUserIdAndContentHash(UUID userId, String contentHash);

    Page<UserDocument> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<UserDocument> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, UserDocument.Status status);

    List<UserDocument> findByStatusIn(Collection<UserDocument.Status> statuses);

    long countByUserIdAndStatus(UUID userId, UserDocument.Status status);

    long countByStatus(UserDocument.Status status);

    @Modifying
    @Query("update UserDocument d set d.status = :status, d.progress = :progress, d.updatedAt = CURRENT_TIMESTAMP where d.id = :id")
    int updateProgress(UUID id, UserDocument.Status status, int progress);

    @Modifying
    @Query("""
            update UserDocument d set d.status = 'READY', d.progress = 100, d.errorMessage = null, d.pageCount = :pageCount,
              d.chunkCount = :chunkCount, d.charCount = :charCount, d.ingestedAt = CURRENT_TIMESTAMP,
              d.updatedAt = CURRENT_TIMESTAMP
            where d.id = :id
            """)
    int markReady(UUID id, Integer pageCount, int chunkCount, int charCount);

    @Modifying
    @Query("""
            update UserDocument d set d.status = 'FAILED', d.errorMessage = :error, d.updatedAt = CURRENT_TIMESTAMP
            where d.id = :id
            """)
    int markFailed(UUID id, String error);
}
