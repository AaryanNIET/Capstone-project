package com.capstone.agenticrag.agent;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.capstone.agenticrag.source.MessageSource;

/** A source registered during a run. {@code citation} is the number the model must use, e.g. [2]. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunSource(int citation, MessageSource.Type type, String title, String url, UUID documentId,
                        Integer chunkIndex, Integer pageNumber, String snippet, Float score, Instant retrievedAt,
                        boolean cited) {

    public RunSource withCited(boolean value) {
        return new RunSource(citation, type, title, url, documentId, chunkIndex, pageNumber, snippet, score,
                retrievedAt, value);
    }
}
