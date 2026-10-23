package com.capstone.agenticrag.source;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A source that a tool actually returned during the run that produced an assistant message. */
@Entity
@Table(name = "message_sources")
public class MessageSource {

    public enum Type { DOCUMENT, WEB, MEMORY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(nullable = false)
    private int citation;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2048)
    private String url;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(columnDefinition = "text")
    private String snippet;

    private Float score;

    @Column(nullable = false)
    private boolean cited;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    protected MessageSource() {}

    public MessageSource(UUID messageId, int citation, Type type, String title, String url, UUID documentId,
                         Integer chunkIndex, Integer pageNumber, String snippet, Float score, boolean cited,
                         Instant retrievedAt) {
        this.messageId = messageId;
        this.citation = citation;
        this.type = type;
        this.title = title;
        this.url = url;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.snippet = snippet;
        this.score = score;
        this.cited = cited;
        this.retrievedAt = retrievedAt;
    }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public int getCitation() { return citation; }
    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public UUID getDocumentId() { return documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public Integer getPageNumber() { return pageNumber; }
    public String getSnippet() { return snippet; }
    public Float getScore() { return score; }
    public boolean isCited() { return cited; }
    public Instant getRetrievedAt() { return retrievedAt; }
}
