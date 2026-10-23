package com.capstone.agenticrag.document;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.agenticrag.common.PageResponse;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.document.DocumentDtos.DocumentIngestionRequested;
import com.capstone.agenticrag.document.DocumentDtos.DocumentResponse;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.rag.EmbeddingService;

@Service
public class DocumentService {

    public static final long MAX_FILE_BYTES = DataSize.ofMegabytes(20).toBytes();
    public static final int MAX_DOCUMENTS_PER_USER = 200;

    private final DocumentRepository documents;
    private final FileStorageService storage;
    private final EmbeddingService embeddings;
    private final ApplicationEventPublisher events;

    public DocumentService(DocumentRepository documents, FileStorageService storage, EmbeddingService embeddings,
                           ApplicationEventPublisher events) {
        this.documents = documents;
        this.storage = storage;
        this.embeddings = embeddings;
        this.events = events;
    }

    @Transactional
    public DocumentResponse upload(UUID userId, MultipartFile file) {
        if (!embeddings.isAvailable()) {
            throw ApiException.unavailable("EMBEDDINGS_UNAVAILABLE",
                    "Document ingestion requires an embedding model. Configure EMBEDDING_PROVIDER and its API key.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Files must be at most 20 MB");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        FileType type = FileType.fromFilename(filename).orElseThrow(() -> new ApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Supported file types are PDF, TXT, MD and DOCX"));
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The upload could not be read");
        }
        if (!type.matchesContent(data)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CONTENT_MISMATCH",
                    "The file content does not match its ." + type.name().toLowerCase() + " extension");
        }
        if (documents.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1)).getTotalElements() >= MAX_DOCUMENTS_PER_USER) {
            throw ApiException.conflict("DOCUMENT_LIMIT", "You have reached the limit of " + MAX_DOCUMENTS_PER_USER + " documents");
        }
        String hash = Texts.sha256(data);
        Optional<UserDocument> duplicate = documents.findByUserIdAndContentHash(userId, hash);
        if (duplicate.isPresent()) {
            throw ApiException.conflict("DUPLICATE_DOCUMENT",
                    "This file was already uploaded as \"" + duplicate.get().getFilename() + "\"");
        }
        UUID id = UUID.randomUUID();
        String path = storage.store(userId, id, data);
        UserDocument doc = documents.save(new UserDocument(id, userId, filename, type.mimeType(), type, data.length, hash, path));
        events.publishEvent(new DocumentIngestionRequested(doc.getId()));
        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(UUID userId, int page, int size) {
        return PageResponse.from(documents.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100))), DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public UserDocument requireOwned(UUID userId, UUID documentId) {
        return documents.findByIdAndUserId(documentId, userId).orElseThrow(() -> ApiException.notFound("Document"));
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        UserDocument doc = requireOwned(userId, documentId);
        documents.delete(doc);
        documents.flush();
        storage.delete(doc.getStoragePath());
    }

    @Transactional
    public DocumentResponse reingest(UUID userId, UUID documentId) {
        UserDocument doc = requireOwned(userId, documentId);
        if (doc.getStatus() == UserDocument.Status.PROCESSING) {
            throw ApiException.conflict("INGESTION_IN_PROGRESS", "This document is currently being processed");
        }
        if (!embeddings.isAvailable()) {
            throw ApiException.unavailable("EMBEDDINGS_UNAVAILABLE", "Document ingestion requires an embedding model.");
        }
        doc.resetForIngestion();
        events.publishEvent(new DocumentIngestionRequested(doc.getId()));
        return DocumentResponse.from(doc);
    }

    /** Keeps only the base name, strips control and path characters, and bounds the length. */
    static String sanitizeFilename(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw ApiException.badRequest("Invalid file name");
        }
        return Texts.truncate(name, 200);
    }
}
