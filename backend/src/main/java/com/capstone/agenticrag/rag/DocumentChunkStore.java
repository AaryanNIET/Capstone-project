package com.capstone.agenticrag.rag;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.capstone.agenticrag.common.Texts;

/**
 * JDBC access to {@code document_chunks}. Every read is filtered by {@code user_id} AND joined to a READY document of
 * the same user, so one user's chunks can never be retrieved for another user. All values are bound parameters.
 */
@Repository
public class DocumentChunkStore {

    public record NewChunk(int index, Integer pageNumber, String section, String content, float[] embedding) {}

    public record RetrievedChunk(UUID chunkId, UUID documentId, String filename, int chunkIndex, Integer pageNumber,
                                 String section, String content, double score) {}

    private static final String SELECT = """
            SELECT c.id, c.document_id, d.filename, c.chunk_index, c.page_number, c.section, c.content, %s AS score
            FROM document_chunks c JOIN documents d ON d.id = c.document_id
            """;

    private static final RowMapper<RetrievedChunk> MAPPER = DocumentChunkStore::map;

    private final JdbcTemplate jdbc;

    public DocumentChunkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteByDocument(UUID documentId) {
        jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
    }

    public void insertAll(UUID documentId, UUID userId, List<NewChunk> chunks) {
        jdbc.batchUpdate("""
                INSERT INTO document_chunks (document_id, user_id, chunk_index, page_number, section, content, char_count, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                """, chunks, 200, (ps, c) -> {
            ps.setObject(1, documentId);
            ps.setObject(2, userId);
            ps.setInt(3, c.index());
            ps.setObject(4, c.pageNumber());
            ps.setString(5, c.section());
            ps.setString(6, c.content());
            ps.setInt(7, c.content().length());
            ps.setString(8, Texts.toVectorLiteral(c.embedding()));
        });
    }

    /** Cosine-similarity search (score = 1 - cosine distance) restricted to the user's ready documents. */
    public List<RetrievedChunk> vectorSearch(UUID userId, float[] queryEmbedding, int limit, List<UUID> documentIds) {
        String vector = Texts.toVectorLiteral(queryEmbedding);
        String docFilter = documentIds == null || documentIds.isEmpty() ? "" : " AND c.document_id = ANY (?)";
        String sql = SELECT.formatted("1 - (c.embedding <=> CAST(? AS vector))")
                + " WHERE c.user_id = ? AND d.user_id = ? AND d.status = 'READY'" + docFilter
                + " ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?";
        return jdbc.query(sql, ps -> {
            int i = 1;
            ps.setString(i++, vector);
            ps.setObject(i++, userId);
            ps.setObject(i++, userId);
            if (!docFilter.isEmpty()) {
                ps.setArray(i++, ps.getConnection().createArrayOf("uuid", documentIds.toArray()));
            }
            ps.setString(i++, vector);
            ps.setInt(i, limit);
        }, MAPPER);
    }

    /** Full-text (OR of content words) search, used to complement vector search and as a fallback. */
    public List<RetrievedChunk> keywordSearch(UUID userId, String query, int limit, List<UUID> documentIds) {
        Set<String> words = Texts.contentWords(query);
        if (words.isEmpty()) {
            return List.of();
        }
        // Words contain only letters/digits (see Texts.contentWords), so this tsquery string is safe; it is still bound.
        String tsQuery = words.stream().limit(30).collect(Collectors.joining(" | "));
        String docFilter = documentIds == null || documentIds.isEmpty() ? "" : " AND c.document_id = ANY (?)";
        String sql = SELECT.formatted("ts_rank_cd(c.content_tsv, to_tsquery('english', ?), 32)")
                + " WHERE c.user_id = ? AND d.user_id = ? AND d.status = 'READY'"
                + " AND c.content_tsv @@ to_tsquery('english', ?)" + docFilter
                + " ORDER BY score DESC LIMIT ?";
        return jdbc.query(sql, ps -> {
            int i = 1;
            ps.setString(i++, tsQuery);
            ps.setObject(i++, userId);
            ps.setObject(i++, userId);
            ps.setString(i++, tsQuery);
            if (!docFilter.isEmpty()) {
                ps.setArray(i++, ps.getConnection().createArrayOf("uuid", documentIds.toArray()));
            }
            ps.setInt(i, limit);
        }, MAPPER);
    }

    /** Chunks of one document in order, evenly sampled down to {@code limit} (used for document summaries). */
    public List<RetrievedChunk> sampleDocument(UUID userId, UUID documentId, int limit) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id = ? AND user_id = ?",
                Integer.class, documentId, userId);
        if (total == null || total == 0) {
            return List.of();
        }
        int step = Math.max(1, (int) Math.ceil(total / (double) limit));
        return jdbc.query(SELECT.formatted("1.0") + """
                 WHERE c.document_id = ? AND c.user_id = ? AND d.user_id = ? AND d.status = 'READY'
                   AND c.chunk_index % ? = 0
                 ORDER BY c.chunk_index LIMIT ?
                """, MAPPER, documentId, userId, userId, step, limit);
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM document_chunks", Long.class);
        return n == null ? 0 : n;
    }

    private static RetrievedChunk map(ResultSet rs, int row) throws SQLException {
        return new RetrievedChunk(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getString("filename"), rs.getInt("chunk_index"), (Integer) rs.getObject("page_number"),
                rs.getString("section"), rs.getString("content"), rs.getDouble("score"));
    }
}
