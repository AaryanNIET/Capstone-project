package com.capstone.agenticrag.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.capstone.agenticrag.chat.AssistantMode;
import com.capstone.agenticrag.source.MessageSource;

/**
 * Mutable, per-request state of one agent run. Identity (user, conversation) is fixed at construction and is the only
 * identity tools ever use, so the model cannot address another user's data.
 */
public final class AgentRunContext {

    private final UUID runId = UUID.randomUUID();
    private final UUID userId;
    private final UUID conversationId;
    private final AssistantMode mode;
    private final String userMessage;
    private final boolean memoryEnabled;
    private final Instant startedAt = Instant.now();
    private final Instant deadline;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private final Map<String, RunSource> sourcesByKey = new LinkedHashMap<>();
    private final List<ToolCallRecord> toolCalls = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> callSignatures = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> searchResultUrls = Collections.synchronizedSet(new HashSet<>());
    private volatile int iteration;
    private volatile boolean usedDocuments;
    private volatile boolean usedWeb;
    private volatile boolean usedMemory;

    public AgentRunContext(UUID userId, UUID conversationId, AssistantMode mode, String userMessage,
                           boolean memoryEnabled, Duration maxDuration) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.mode = mode;
        this.userMessage = userMessage;
        this.memoryEnabled = memoryEnabled;
        this.deadline = startedAt.plus(maxDuration);
    }

    /**
     * Registers a source and returns its citation number; the same chunk or URL keeps the number it already has.
     */
    public synchronized RunSource addSource(String dedupeKey, Supplier<RunSource> factory) {
        RunSource existing = sourcesByKey.get(dedupeKey);
        if (existing != null) {
            return existing;
        }
        RunSource draft = factory.get();
        RunSource numbered = new RunSource(sourcesByKey.size() + 1, draft.type(), draft.title(), draft.url(),
                draft.documentId(), draft.chunkIndex(), draft.pageNumber(), draft.snippet(), draft.score(),
                draft.retrievedAt(), false);
        sourcesByKey.put(dedupeKey, numbered);
        if (numbered.type() == MessageSource.Type.WEB && numbered.url() != null) {
            searchResultUrls.add(numbered.url());
        }
        return numbered;
    }

    public synchronized List<RunSource> sources() {
        return List.copyOf(sourcesByKey.values());
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public Duration remaining() {
        Duration d = Duration.between(Instant.now(), deadline);
        return d.isNegative() ? Duration.ZERO : d;
    }

    public boolean isExpired() {
        return remaining().isZero();
    }

    /** Returns false when the identical call (same tool and arguments) was already made in this run. */
    public boolean registerCall(String signature) {
        return callSignatures.add(signature);
    }

    public void record(ToolCallRecord record) {
        toolCalls.add(record);
    }

    public List<ToolCallRecord> toolCalls() {
        synchronized (toolCalls) {
            return List.copyOf(toolCalls);
        }
    }

    public int executedToolCalls() {
        synchronized (toolCalls) {
            return (int) toolCalls.stream().filter(t -> t.status() != ToolCallRecord.Status.REJECTED).count();
        }
    }

    public boolean isSearchResultUrl(String url) {
        return searchResultUrls.contains(url);
    }

    public UUID runId() { return runId; }
    public UUID userId() { return userId; }
    public UUID conversationId() { return conversationId; }
    public AssistantMode mode() { return mode; }
    public String userMessage() { return userMessage; }
    public boolean memoryEnabled() { return memoryEnabled; }
    public Instant startedAt() { return startedAt; }
    public int iteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = iteration; }
    public boolean usedDocuments() { return usedDocuments; }
    public void markUsedDocuments() { this.usedDocuments = true; }
    public boolean usedWeb() { return usedWeb; }
    public void markUsedWeb() { this.usedWeb = true; }
    public boolean usedMemory() { return usedMemory; }
    public void markUsedMemory() { this.usedMemory = true; }
}
