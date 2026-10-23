package com.capstone.agenticrag.feedback;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseFeedbackRepository extends JpaRepository<ResponseFeedback, UUID> {

    Optional<ResponseFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);

    List<ResponseFeedback> findByUserIdAndMessageIdIn(UUID userId, Collection<UUID> messageIds);

    long countByRating(ResponseFeedback.Rating rating);
}
