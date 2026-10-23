package com.capstone.agenticrag.chat;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.capstone.agenticrag.agent.AgentListener;
import com.capstone.agenticrag.agent.AgentOrchestrator;
import com.capstone.agenticrag.agent.AgentRequest;
import com.capstone.agenticrag.agent.AgentResult;
import com.capstone.agenticrag.agent.AgentRunContext;
import com.capstone.agenticrag.agent.RunSource;
import com.capstone.agenticrag.agent.ToolCallRecord;
import com.capstone.agenticrag.chat.ChatDtos.ChatResponse;
import com.capstone.agenticrag.chat.ChatDtos.MessageDto;
import com.capstone.agenticrag.chat.ChatDtos.SendMessageRequest;
import com.capstone.agenticrag.chat.ChatTurnService.PreparedTurn;
import com.capstone.agenticrag.config.AiModels;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.memory.Memory;
import com.capstone.agenticrag.memory.MemoryExtractionService;
import com.capstone.agenticrag.memory.MemoryService;

/**
 * Runs one chat turn: persist the user message, save memories from it, retrieve relevant memories, run the agent,
 * persist the reply. Available as a blocking call or as a Server-Sent Events stream with these events:
 * <ul>
 *   <li>{@code meta}: conversation id, title, mode and the stored user message</li>
 *   <li>{@code status}: a user-facing progress stage such as "Thinking"</li>
 *   <li>{@code tool}: a tool started or finished</li>
 *   <li>{@code content}: a fragment of the answer</li>
 *   <li>{@code reset}: discard the content streamed so far (it was a preamble to tool calls)</li>
 *   <li>{@code sources}: the sources registered in this run</li>
 *   <li>{@code done}: the stored assistant message (also sent for cancelled and failed runs)</li>
 *   <li>{@code error}: the run could not be completed or stored</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatTurnService turns;
    private final ConversationService conversations;
    private final ActiveRunRegistry runs;
    private final AgentOrchestrator orchestrator;
    private final MemoryExtractionService memoryExtraction;
    private final MemoryService memories;
    private final ConversationSummaryService summaries;
    private final AiModels models;
    private final ExecutorService executor;
    private final AppProperties.Agent config;

    public ChatService(ChatTurnService turns, ConversationService conversations, ActiveRunRegistry runs,
                       AgentOrchestrator orchestrator, MemoryExtractionService memoryExtraction, MemoryService memories,
                       ConversationSummaryService summaries, AiModels models,
                       @Qualifier("agentExecutor") ExecutorService executor, AppProperties props) {
        this.turns = turns;
        this.conversations = conversations;
        this.runs = runs;
        this.orchestrator = orchestrator;
        this.memoryExtraction = memoryExtraction;
        this.memories = memories;
        this.summaries = summaries;
        this.models = models;
        this.executor = executor;
        this.config = props.agent();
    }

    /** A turn that has been validated, reserved and prepared; the agent has not run yet. */
    private record StartedTurn(ActiveRunRegistry.ActiveRun slot, PreparedTurn turn, MessageDto userMessage) {}

    // ------------------------------------------------------------------ public API

    public ChatResponse send(UUID userId, UUID conversationId, SendMessageRequest req) {
        StartedTurn started = start(userId, conversationId, req);
        MessageDto reply = execute(started, AgentListener.NOOP);
        return new ChatResponse(started.userMessage(), reply);
    }

    /**
     * Validates and stores the user message on the request thread, so problems such as a missing conversation or a run
     * already in progress become ordinary HTTP errors; the agent then runs on a virtual thread and streams events.
     */
    public SseEmitter stream(UUID userId, UUID conversationId, SendMessageRequest req) {
        StartedTurn started = start(userId, conversationId, req);
        SseEmitter emitter = new SseEmitter(config.maxDuration().plusSeconds(60).toMillis());
        SseListener listener = new SseListener(emitter);
        emitter.onTimeout(listener::disconnect);
        emitter.onError(e -> listener.disconnect());

        PreparedTurn t = started.turn();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("conversationId", t.conversationId());
        meta.put("title", t.title());
        meta.put("mode", t.mode());
        meta.put("regenerate", t.regenerate());
        if (t.replacedAssistantId() != null) {
            meta.put("replacesMessageId", t.replacedAssistantId());
        }
        meta.put("userMessage", started.userMessage());
        listener.send("meta", meta);

        try {
            executor.submit(() -> {
                try {
                    MessageDto reply = execute(started, listener);
                    listener.send("sources", Map.of("sources", reply.sources() == null ? List.of() : reply.sources()));
                    listener.send("done", reply);
                } catch (RuntimeException e) {
                    log.error("Chat run failed for conversation {}", conversationId, e);
                    listener.send("error", Map.of("code", "INTERNAL_ERROR",
                            "message", "Something went wrong while generating the response."));
                } finally {
                    listener.complete();
                }
            });
        } catch (RejectedExecutionException e) {
            runs.release(started.slot());
            throw e;
        }
        return emitter;
    }

    public boolean cancel(UUID userId, UUID conversationId) {
        conversations.requireOwned(userId, conversationId);
        return runs.cancel(userId, conversationId);
    }

    // ------------------------------------------------------------------ turn execution

    private StartedTurn start(UUID userId, UUID conversationId, SendMessageRequest req) {
        conversations.requireOwned(userId, conversationId);
        ActiveRunRegistry.ActiveRun slot = runs.reserve(conversationId, userId);
        try {
            PreparedTurn turn = turns.prepare(userId, conversationId, req);
            return new StartedTurn(slot, turn, turns.toDto(userId, turn.userMessage()));
        } catch (RuntimeException e) {
            runs.release(slot);
            throw e;
        }
    }

    /** Runs the agent and stores the reply. Always releases the conversation's run slot. */
    private MessageDto execute(StartedTurn started, AgentListener listener) {
        PreparedTurn t = started.turn();
        Instant startedAt = Instant.now();
        AgentRunContext ctx = new AgentRunContext(t.userId(), t.conversationId(), t.mode(),
                t.userMessage().getContent(), t.memoryEnabled(), config.maxDuration());
        started.slot().attach(ctx);
        try {
            AgentResult result;
            try {
                result = orchestrator.run(buildRequest(t, ctx, listener), listener);
            } catch (RuntimeException e) {
                log.error("Agent run crashed for conversation {}", t.conversationId(), e);
                result = new AgentResult("", AgentResult.Status.FAILED, "INTERNAL_ERROR",
                        "Something went wrong while generating the response. Please try again.", ctx.iteration(),
                        ctx.toolCalls(), List.<RunSource>of(), false, false, false, null, null);
            }
            if (result.status() == AgentResult.Status.CANCELLED) {
                listener.onStatus("cancelled", "Stopped");
            }
            MessageDto reply = turns.complete(t, ctx, result, models.chatModelName(), startedAt);
            if (result.status() == AgentResult.Status.COMPLETED) {
                summaries.summarizeAsync(t.userId(), t.conversationId());
            }
            return reply;
        } finally {
            runs.release(started.slot());
        }
    }

    private AgentRequest buildRequest(PreparedTurn t, AgentRunContext ctx, AgentListener listener) {
        List<String> savedNotes = List.of();
        List<Memory> relevant = List.of();
        if (t.memoryEnabled()) {
            if (!t.regenerate()) {
                listener.onStatus("memory", "Checking what to remember");
                savedNotes = memoryExtraction.extractAndSave(t.userId(), t.conversationId(), t.userMessage().getId(),
                        t.userMessage().getContent());
            }
            try {
                relevant = memories.retrieveRelevant(t.userId(), t.userMessage().getContent(), null).stream()
                        .map(MemoryService.ScoredMemory::memory).toList();
            } catch (RuntimeException e) {
                log.warn("Memory retrieval failed; continuing without memories: {}", e.getClass().getSimpleName());
            }
        }
        return new AgentRequest(ctx, t.userName(), t.history(), t.summary(), relevant, savedNotes, t.readyDocuments());
    }

    // ------------------------------------------------------------------ SSE

    /** Forwards agent progress as SSE events. A client that disconnects stops receiving, but the run still completes. */
    private static final class SseListener implements AgentListener {

        private final SseEmitter emitter;
        private volatile boolean open = true;

        SseListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onStatus(String stage, String label) {
            send("status", Map.of("stage", stage, "label", label));
        }

        @Override
        public void onToolStarted(String tool, String label) {
            send("tool", Map.of("tool", tool, "label", label, "state", "started"));
        }

        @Override
        public void onToolFinished(String tool, ToolCallRecord.Status status, long durationMs) {
            send("tool", Map.of("tool", tool, "state", "finished", "status", status, "durationMs", durationMs));
        }

        @Override
        public void onContent(String delta) {
            send("content", Map.of("delta", delta));
        }

        @Override
        public void onContentReset() {
            send("reset", Map.of());
        }

        synchronized void send(String event, Object data) {
            if (!open) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                open = false;
            }
        }

        void disconnect() {
            open = false;
        }

        synchronized void complete() {
            if (open) {
                open = false;
                try {
                    emitter.complete();
                } catch (IllegalStateException ignored) {
                    // already completed by the container
                }
            }
        }
    }
}
