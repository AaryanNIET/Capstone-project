package com.capstone.agenticrag.agent;

/**
 * Receives progress from an agent run (e.g. to forward as SSE events). Only user-facing status is reported; hidden
 * reasoning, prompts and raw tool payloads are never exposed.
 */
public interface AgentListener {

    /** A short, user-friendly status such as "Searching your documents". */
    default void onStatus(String stage, String label) {}

    default void onToolStarted(String tool, String label) {}

    default void onToolFinished(String tool, ToolCallRecord.Status status, long durationMs) {}

    /** A streamed fragment of the answer text. */
    default void onContent(String delta) {}

    /** Text streamed so far was a preamble to tool calls (e.g. "Let me check") and should be discarded. */
    default void onContentReset() {}

    AgentListener NOOP = new AgentListener() {};
}
