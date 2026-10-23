package com.capstone.agenticrag.document;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class DocumentDtos {

    private DocumentDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DocumentResponse(UUID id, String filename, FileType fileType, long sizeBytes,
                                   UserDocument.Status status, int progress, String errorMessage, Integer pageCount,
                                   int chunkCount, int charCount, Instant createdAt, Instant updatedAt,
                                   Instant ingestedAt) {

        public static DocumentResponse from(UserDocument d) {
            return new DocumentResponse(d.getId(), d.getFilename(), d.getFileType(), d.getSizeBytes(), d.getStatus(),
                    d.getProgress(), d.getErrorMessage(), d.getPageCount(), d.getChunkCount(), d.getCharCount(),
                    d.getCreatedAt(), d.getUpdatedAt(), d.getIngestedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DocumentStatusResponse(UUID id, UserDocument.Status status, int progress, String errorMessage,
                                         int chunkCount) {

        public static DocumentStatusResponse from(UserDocument d) {
            return new DocumentStatusResponse(d.getId(), d.getStatus(), d.getProgress(), d.getErrorMessage(),
                    d.getChunkCount());
        }
    }

    /** Published after the upload transaction commits; triggers asynchronous ingestion. */
    public record DocumentIngestionRequested(UUID documentId) {}
}
