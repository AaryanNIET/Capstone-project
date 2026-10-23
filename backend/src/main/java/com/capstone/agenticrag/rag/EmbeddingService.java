package com.capstone.agenticrag.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import com.capstone.agenticrag.config.AiModels;
import com.capstone.agenticrag.exception.ApiException;

/** Wraps the embedding model with batching and dimension validation against the pgvector column size. */
@Service
public class EmbeddingService {

    private static final int BATCH_SIZE = 32;

    private final AiModels models;

    public EmbeddingService(AiModels models) {
        this.models = models;
    }

    public boolean isAvailable() {
        return models.embeddingAvailable();
    }

    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel model = models.embedding().orElseThrow(() -> ApiException.unavailable("EMBEDDINGS_UNAVAILABLE",
                "No embedding model is configured. Set EMBEDDING_PROVIDER and its API key."));
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<float[]> batch = model.embed(texts.subList(i, Math.min(texts.size(), i + BATCH_SIZE)));
            for (float[] vector : batch) {
                if (vector.length != models.embeddingDimensions()) {
                    throw ApiException.unavailable("EMBEDDING_DIMENSION_MISMATCH", "The embedding model returned "
                            + vector.length + " dimensions but EMBEDDING_DIMENSIONS is " + models.embeddingDimensions());
                }
                out.add(vector);
            }
        }
        return out;
    }
}
