package com.capstone.agenticrag.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {

    Page<AgentExecution> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<AgentExecution> findByConversationIdOrderByStartedAtAsc(UUID conversationId);

    long countByStatus(AgentResult.Status status);

    @Query("select avg(e.durationMs) from AgentExecution e where e.startedAt > :since")
    Double averageDurationSince(Instant since);

    long countByStartedAtAfter(Instant since);
}
