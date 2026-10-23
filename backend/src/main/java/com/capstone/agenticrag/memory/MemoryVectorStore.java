package com.capstone.agenticrag.memory;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.capstone.agenticrag.common.Texts;

/** Embedding column access for memories; every query is scoped to one user. */
@Repository
public class MemoryVectorStore {

    public record Match(UUID id, double score) {}

    private final JdbcTemplate jdbc;

    public MemoryVectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void setEmbedding(UUID memoryId, float[] embedding) {
        jdbc.update("UPDATE memories SET embedding = CAST(? AS vector) WHERE id = ?",
                embedding == null ? null : Texts.toVectorLiteral(embedding), memoryId);
    }

    public List<Match> nearest(UUID userId, float[] query, int limit) {
        String v = Texts.toVectorLiteral(query);
        return jdbc.query("""
                SELECT id, 1 - (embedding <=> CAST(? AS vector)) AS score FROM memories
                WHERE user_id = ? AND embedding IS NOT NULL AND (expires_at IS NULL OR expires_at > now())
                ORDER BY embedding <=> CAST(? AS vector) LIMIT ?
                """, (rs, i) -> new Match(rs.getObject("id", UUID.class), rs.getDouble("score")), v, userId, v, limit);
    }
}
