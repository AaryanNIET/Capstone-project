package com.capstone.agenticrag.config;

import java.util.Optional;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Access point for the configured language and embedding models. Either may be absent when its provider is not
 * configured; dependent features then report a clear "not configured" error instead of failing at startup.
 */
public interface AiModels {

    Optional<ChatModel> chat();

    Optional<EmbeddingModel> embedding();

    String chatModelName();

    String embeddingModelName();

    int embeddingDimensions();

    default boolean chatAvailable() {
        return chat().isPresent();
    }

    default boolean embeddingAvailable() {
        return embedding().isPresent();
    }
}
