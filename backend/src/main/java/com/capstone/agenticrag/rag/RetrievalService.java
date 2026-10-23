package com.capstone.agenticrag.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.rag.DocumentChunkStore.RetrievedChunk;

/**
 * Hybrid retrieval over the user's documents: vector similarity (above a configurable threshold) complemented by
 * full-text matches. If the embedding provider fails, keyword search alone keeps retrieval working.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    public record RetrievalResult(List<RetrievedChunk> chunks, boolean semantic) {}

    private final DocumentChunkStore store;
    private final EmbeddingService embeddings;
    private final VectorSupport vectorSupport;
    private final AppProperties.Rag config;

    public RetrievalService(DocumentChunkStore store, EmbeddingService embeddings, VectorSupport vectorSupport,
                            AppProperties props) {
        this.store = store;
        this.embeddings = embeddings;
        this.vectorSupport = vectorSupport;
        this.config = props.rag();
    }

    @Transactional(readOnly = true)
    public RetrievalResult search(UUID userId, String query, Integer topK, List<UUID> documentIds) {
        int k = topK == null ? config.topK() : Math.clamp(topK, 1, 20);
        Map<UUID, RetrievedChunk> merged = new LinkedHashMap<>();
        boolean semantic = false;
        if (embeddings.isAvailable()) {
            try {
                float[] q = embeddings.embed(query);
                vectorSupport.applySearchSettings();
                for (RetrievedChunk c : store.vectorSearch(userId, q, k, documentIds)) {
                    if (c.score() >= config.similarityThreshold()) {
                        merged.put(c.chunkId(), c);
                    }
                }
                semantic = true;
            } catch (RuntimeException e) {
                log.warn("Vector search unavailable, falling back to keyword search: {}", e.getClass().getSimpleName());
            }
        }
        if (merged.size() < k) {
            for (RetrievedChunk c : store.keywordSearch(userId, query, k, documentIds)) {
                if (merged.size() >= k) {
                    break;
                }
                merged.putIfAbsent(c.chunkId(), c);
            }
        }
        return new RetrievalResult(new ArrayList<>(merged.values()), semantic);
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> sampleDocument(UUID userId, UUID documentId, int limit) {
        return store.sampleDocument(userId, documentId, limit);
    }
}
