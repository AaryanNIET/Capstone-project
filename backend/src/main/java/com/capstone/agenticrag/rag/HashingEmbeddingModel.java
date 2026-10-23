package com.capstone.agenticrag.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import com.capstone.agenticrag.common.Texts;

/**
 * Deterministic feature-hashing "embedding" (unigrams + bigrams, L2-normalised). It captures lexical overlap only and
 * is NOT a semantic model. It exists for offline demos and for the automated test suite, which must not call paid APIs.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public HashingEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> results = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            results.add(new Embedding(vectorize(inputs.get(i)), i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    float[] vectorize(String text) {
        float[] v = new float[dimensions];
        String[] tokens = Texts.normalizeForComparison(text == null ? "" : text).split(" ");
        String previous = null;
        for (String token : tokens) {
            if (token.isEmpty() || Texts.STOP_WORDS.contains(token)) {
                continue;
            }
            String stem = stem(token);
            add(v, stem, 1.0f);
            if (previous != null) {
                add(v, previous + "_" + stem, 0.5f);
            }
            previous = stem;
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1e-6f; // pgvector cannot compute cosine distance for a zero vector
            return v;
        }
        float inv = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
        return v;
    }

    private void add(float[] v, String feature, float weight) {
        CRC32 crc = new CRC32();
        crc.update(feature.getBytes(StandardCharsets.UTF_8));
        long h = crc.getValue();
        int index = (int) (h % dimensions);
        float sign = ((h >>> 20) & 1) == 0 ? 1f : -1f;
        v[index] += sign * weight;
    }

    /** Very small suffix stripper so "policies"/"policy" and "refunds"/"refund" collide. */
    private static String stem(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
