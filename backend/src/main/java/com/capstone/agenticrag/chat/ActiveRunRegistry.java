package com.capstone.agenticrag.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.capstone.agenticrag.agent.AgentRunContext;
import com.capstone.agenticrag.exception.ApiException;

/**
 * Tracks the agent run in progress for each conversation. Only one run per conversation may be active, and only its
 * owner can cancel it. State is in memory, which suits a single backend instance.
 */
@Component
public class ActiveRunRegistry {

    /** A reserved slot. The run context is attached once the turn is prepared; a cancel may arrive before that. */
    public static final class ActiveRun {
        private final UUID conversationId;
        private final UUID userId;
        private volatile AgentRunContext context;
        private volatile boolean cancelled;

        private ActiveRun(UUID conversationId, UUID userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        public synchronized void attach(AgentRunContext ctx) {
            this.context = ctx;
            if (cancelled) {
                ctx.cancel();
            }
        }

        synchronized void cancel() {
            cancelled = true;
            if (context != null) {
                context.cancel();
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public UUID conversationId() { return conversationId; }
        public UUID userId() { return userId; }
    }

    private final Map<UUID, ActiveRun> runs = new ConcurrentHashMap<>();

    /** Reserves the conversation for a new run, or fails with 409 when a run is already in progress. */
    public ActiveRun reserve(UUID conversationId, UUID userId) {
        ActiveRun run = new ActiveRun(conversationId, userId);
        if (runs.putIfAbsent(conversationId, run) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_IN_PROGRESS",
                    "A response is already being generated in this conversation. Wait for it or stop it first.");
        }
        return run;
    }

    public void release(ActiveRun run) {
        runs.remove(run.conversationId(), run);
    }

    /** Cancels the active run of the conversation if it belongs to the user. Returns whether a run was cancelled. */
    public boolean cancel(UUID userId, UUID conversationId) {
        ActiveRun run = runs.get(conversationId);
        if (run == null || !run.userId().equals(userId)) {
            return false;
        }
        run.cancel();
        return true;
    }

    public boolean isActive(UUID conversationId) {
        return runs.containsKey(conversationId);
    }

    public int activeCount() {
        return runs.size();
    }
}
