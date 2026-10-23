package com.capstone.agenticrag.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.document.DocumentDtos.DocumentIngestionRequested;
import com.capstone.agenticrag.document.TextExtractor.ExtractionException;
import com.capstone.agenticrag.document.TextExtractor.Extracted;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.rag.DocumentChunkStore;
import com.capstone.agenticrag.rag.DocumentChunkStore.NewChunk;
import com.capstone.agenticrag.rag.EmbeddingService;
import com.capstone.agenticrag.rag.TextChunker;
import com.capstone.agenticrag.rag.TextChunker.Chunk;

/**
 * Asynchronous ingestion: read file, extract and clean text, chunk with overlap, embed in batches, then atomically
 * replace the document's chunks and mark it READY. Progress is persisted so the UI can poll it.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int EMBED_BATCH = 32;

    private final DocumentRepository documents;
    private final FileStorageService storage;
    private final TextExtractor extractor;
    private final EmbeddingService embeddings;
    private final DocumentChunkStore chunks;
    private final TransactionTemplate tx;
    private final AppProperties.Rag config;

    public DocumentIngestionService(DocumentRepository documents, FileStorageService storage, TextExtractor extractor,
                                    EmbeddingService embeddings, DocumentChunkStore chunks, TransactionTemplate tx,
                                    AppProperties props) {
        this.documents = documents;
        this.storage = storage;
        this.extractor = extractor;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.tx = tx;
        this.config = props.rag();
    }

    @Async("ingestionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngestionRequested(DocumentIngestionRequested event) {
        ingest(event.documentId());
    }

    /** Runs the full pipeline synchronously. Never throws; failures are recorded on the document. */
    public void ingest(UUID documentId) {
        UserDocument doc = documents.findById(documentId).orElse(null);
        if (doc == null) {
            return; // deleted before processing started
        }
        long started = System.currentTimeMillis();
        try {
            progress(documentId, 5);
            byte[] data = storage.read(doc.getStoragePath());
            Extracted extracted = extractor.extract(doc.getFileType(), data);
            if (extracted.totalChars() == 0) {
                throw new ExtractionException("The document does not contain any text.");
            }
            progress(documentId, 25);

            List<Chunk> pieces = new TextChunker(config.chunkSize(), config.chunkOverlap()).chunk(extracted.sections());
            if (pieces.size() > config.maxChunksPerDocument()) {
                throw new ExtractionException("The document is too large (more than " + config.maxChunksPerDocument()
                        + " chunks). Please split it into smaller files.");
            }
            progress(documentId, 35);

            List<NewChunk> rows = new ArrayList<>(pieces.size());
            for (int i = 0; i < pieces.size(); i += EMBED_BATCH) {
                List<Chunk> batch = pieces.subList(i, Math.min(pieces.size(), i + EMBED_BATCH));
                List<float[]> vectors = embeddings.embedAll(batch.stream().map(c -> embeddingInput(doc, c)).toList());
                for (int j = 0; j < batch.size(); j++) {
                    Chunk c = batch.get(j);
                    rows.add(new NewChunk(c.index(), c.pageNumber(), c.section(), c.text(), vectors.get(j)));
                }
                progress(documentId, 35 + (int) (55.0 * Math.min(pieces.size(), i + EMBED_BATCH) / pieces.size()));
            }

            boolean stored = Boolean.TRUE.equals(tx.execute(status -> {
                if (documents.findById(documentId).isEmpty()) {
                    return false; // deleted while processing
                }
                chunks.deleteByDocument(documentId);
                chunks.insertAll(documentId, doc.getUserId(), rows);
                documents.markReady(documentId, extracted.pageCount(), rows.size(), extracted.totalChars());
                return true;
            }));
            if (stored) {
                log.info("Ingested document {} type={} chunks={} in {} ms", documentId, doc.getFileType(), rows.size(),
                        System.currentTimeMillis() - started);
            }
        } catch (ExtractionException e) {
            fail(documentId, e.getMessage());
        } catch (ApiException e) {
            fail(documentId, e.getMessage());
        } catch (IOException e) {
            log.error("Stored file missing for document {}", documentId);
            fail(documentId, "The stored file could not be read. Please upload it again.");
        } catch (RuntimeException e) {
            log.error("Ingestion failed for document {}", documentId, e);
            fail(documentId, "Processing failed because the embedding service or database was unavailable. Try re-ingesting.");
        }
    }

    /** Documents left PROCESSING/UPLOADED by a crash or restart are marked failed so the user can re-ingest them. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        tx.executeWithoutResult(status -> documents.findByStatusIn(EnumSet.of(UserDocument.Status.PROCESSING,
                UserDocument.Status.UPLOADED)).forEach(d -> {
            if (d.getUpdatedAt() != null && d.getUpdatedAt().isBefore(java.time.Instant.now().minusSeconds(60))) {
                documents.markFailed(d.getId(), "Processing was interrupted by a server restart. Please re-ingest.");
            }
        }));
    }

    /** Prefixes the filename and heading so chunk embeddings carry document context. */
    private static String embeddingInput(UserDocument doc, Chunk c) {
        StringBuilder sb = new StringBuilder(doc.getFilename());
        if (c.section() != null) {
            sb.append(" - ").append(c.section());
        }
        return sb.append("\n").append(c.text()).toString();
    }

    private void progress(UUID id, int percent) {
        tx.executeWithoutResult(s -> documents.updateProgress(id, UserDocument.Status.PROCESSING, Math.min(99, percent)));
    }

    private void fail(UUID id, String message) {
        log.info("Document {} failed ingestion: {}", id, message);
        tx.executeWithoutResult(s -> documents.markFailed(id, message));
    }
}
