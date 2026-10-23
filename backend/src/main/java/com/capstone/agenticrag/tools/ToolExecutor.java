package com.capstone.agenticrag.tools;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.capstone.agenticrag.agent.AgentListener;
import com.capstone.agenticrag.agent.AgentRunContext;
import com.capstone.agenticrag.agent.ToolCallRecord;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.config.AppProperties;

/**
 * Validates and executes a tool call requested by the model. The model only proposes calls; this guard decides:
 * the tool must be on the run's allow-list, arguments must be a JSON object within size limits, the per-run call
 * budget must not be exhausted, identical repeated calls are refused (loop protection), and execution is bounded by
 * a timeout. Every outcome, including refusals, is returned to the model as a structured JSON result.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final int MAX_ARGUMENT_CHARS = 4000;

    private final ExecutorService executor;
    private final ObjectMapper json;
    private final AppProperties.Agent config;

    public ToolExecutor(@Qualifier("agentExecutor") ExecutorService executor, ObjectMapper json, AppProperties props) {
        this.executor = executor;
        this.json = json;
        this.config = props.agent();
    }

    public ToolResponseMessage.ToolResponse execute(AssistantMessage.ToolCall call, Map<String, ToolCallback> allowed,
                                                    AgentRunContext ctx, AgentListener listener) {
        String id = call.id() == null || call.id().isBlank() ? "call_" + UUID.randomUUID() : call.id();
        String name = call.name() == null ? "" : call.name();
        String args = call.arguments() == null || call.arguments().isBlank() ? "{}" : call.arguments();
        String argSummary = Texts.truncate(args, 300);

        ToolCallback callback = allowed.get(name);
        if (callback == null) {
            return reject(ctx, id, name, argSummary, "Tool '" + Texts.truncate(name, 60) + "' does not exist or is not available in this mode.");
        }
        if (ctx.executedToolCalls() >= config.maxToolCalls()) {
            return reject(ctx, id, name, argSummary, "Tool-call limit reached for this request. Answer now with the information you already have.");
        }
        if (args.length() > MAX_ARGUMENT_CHARS) {
            return reject(ctx, id, name, argSummary, "Arguments are too large.");
        }
        JsonNode parsed;
        try {
            parsed = json.readTree(args);
        } catch (Exception e) {
            return reject(ctx, id, name, argSummary, "Arguments must be a valid JSON object.");
        }
        if (parsed == null || !parsed.isObject()) {
            return reject(ctx, id, name, argSummary, "Arguments must be a JSON object.");
        }
        if (!ctx.registerCall(name + ":" + parsed)) {
            return reject(ctx, id, name, argSummary, "This exact call was already made in this request. Reuse its result instead of repeating it.");
        }
        Duration timeout = min(config.toolTimeout(), ctx.remaining());
        if (timeout.isZero() || ctx.isCancelled()) {
            return reject(ctx, id, name, argSummary, "No time left for further tool calls. Answer now.");
        }

        listener.onToolStarted(name, ToolNames.label(name));
        long started = System.nanoTime();
        Future<String> future = executor.submit(() -> callback.call(parsed.toString()));
        String result;
        ToolCallRecord.Status status;
        String error = null;
        try {
            result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            String resultStatus = statusOf(result);
            status = "error".equals(resultStatus) ? ToolCallRecord.Status.ERROR : ToolCallRecord.Status.OK;
            if (status == ToolCallRecord.Status.ERROR) {
                error = Texts.truncate(result, 300);
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            status = ToolCallRecord.Status.TIMEOUT;
            error = "timed out after " + timeout.toSeconds() + "s";
            result = errorJson("The tool timed out. Continue without it or try a simpler request.");
        } catch (ExecutionException e) {
            log.warn("Tool {} failed: {}", name, e.getCause() == null ? e.toString() : e.getCause().toString());
            status = ToolCallRecord.Status.ERROR;
            error = "execution failed";
            result = errorJson("The tool failed to run. Continue without it and tell the user if this limits the answer.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            status = ToolCallRecord.Status.ERROR;
            error = "interrupted";
            result = errorJson("The request was cancelled.");
        }
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        ctx.record(new ToolCallRecord(name, argSummary, status, durationMs, error, ctx.iteration()));
        listener.onToolFinished(name, status, durationMs);
        return new ToolResponseMessage.ToolResponse(id, name, Texts.truncate(result, config.maxToolResultChars()));
    }

    private ToolResponseMessage.ToolResponse reject(AgentRunContext ctx, String id, String name, String args, String reason) {
        ctx.record(new ToolCallRecord(Texts.truncate(name, 60), args, ToolCallRecord.Status.REJECTED, 0, reason, ctx.iteration()));
        return new ToolResponseMessage.ToolResponse(id, name, errorJson(reason));
    }

    private String statusOf(String result) {
        try {
            return result == null ? null : json.readTree(result).path("status").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String errorJson(String message) {
        return json.createObjectNode().put("status", "error").put("message", message).toString();
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
