package com.capstone.agenticrag.document;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.agenticrag.common.PageResponse;
import com.capstone.agenticrag.document.DocumentDtos.DocumentResponse;
import com.capstone.agenticrag.document.DocumentDtos.DocumentStatusResponse;
import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF, TXT, MD or DOCX file (max 20 MB); ingestion runs asynchronously")
    public ResponseEntity<DocumentResponse> upload(@CurrentUser UserPrincipal me, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documents.upload(me.id(), file));
    }

    @GetMapping
    @Operation(summary = "List the user's documents")
    public PageResponse<DocumentResponse> list(@CurrentUser UserPrincipal me,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return documents.list(me.id(), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document details")
    public DocumentResponse get(@CurrentUser UserPrincipal me, @PathVariable UUID id) {
        return DocumentResponse.from(documents.requireOwned(me.id(), id));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get ingestion status and progress")
    public DocumentStatusResponse status(@CurrentUser UserPrincipal me, @PathVariable UUID id) {
        return DocumentStatusResponse.from(documents.requireOwned(me.id(), id));
    }

    @PostMapping("/{id}/reingest")
    @Operation(summary = "Re-run extraction, chunking and embedding for a document")
    public ResponseEntity<DocumentResponse> reingest(@CurrentUser UserPrincipal me, @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documents.reingest(me.id(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document, its stored file and all of its chunks")
    public ResponseEntity<Void> delete(@CurrentUser UserPrincipal me, @PathVariable UUID id) {
        documents.delete(me.id(), id);
        return ResponseEntity.noContent().build();
    }
}
