package com.capstone.agenticrag.source;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceDto(int citation, MessageSource.Type type, String title, String url, UUID documentId,
                        Integer chunkIndex, Integer pageNumber, String snippet, Float score, boolean cited,
                        Instant retrievedAt) {

    public static SourceDto from(MessageSource s) {
        return new SourceDto(s.getCitation(), s.getType(), s.getTitle(), s.getUrl(), s.getDocumentId(),
                s.getChunkIndex(), s.getPageNumber(), s.getSnippet(), s.getScore(), s.isCited(), s.getRetrievedAt());
    }
}
