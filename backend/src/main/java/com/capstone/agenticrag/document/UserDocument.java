package com.capstone.agenticrag.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** An uploaded file and its ingestion state. Its chunks and vectors live in {@code document_chunks}. */
@Entity
@Table(name = "documents")
public class UserDocument {

    public enum Status { UPLOADED, PROCESSING, READY, FAILED }

    /** Assigned by the application so the file can be stored under its id before the row is inserted. */
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private FileType fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UPLOADED;

    @Column(nullable = false)
    private int progress;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    protected UserDocument() {}

    public UserDocument(UUID id, UUID userId, String filename, String contentType, FileType fileType, long sizeBytes,
                        String contentHash, String storagePath) {
        this.id = id;
        this.userId = userId;
        this.filename = filename;
        this.contentType = contentType;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.contentHash = contentHash;
        this.storagePath = storagePath;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void resetForIngestion() {
        status = Status.UPLOADED;
        progress = 0;
        errorMessage = null;
        chunkCount = 0;
        charCount = 0;
        pageCount = null;
        ingestedAt = null;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public FileType getFileType() { return fileType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getContentHash() { return contentHash; }
    public String getStoragePath() { return storagePath; }
    public Status getStatus() { return status; }
    public int getProgress() { return progress; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getPageCount() { return pageCount; }
    public int getChunkCount() { return chunkCount; }
    public int getCharCount() { return charCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getIngestedAt() { return ingestedAt; }
}
