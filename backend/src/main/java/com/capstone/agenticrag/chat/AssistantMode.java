package com.capstone.agenticrag.chat;

/**
 * Assistant modes the user can switch between. The mode restricts which tools the agent may call.
 */
public enum AssistantMode {
    /** The agent decides which sources it needs (documents, live web, memory). */
    AUTO,
    /** Answers must be grounded in the user's uploaded documents. */
    DOCUMENTS,
    /** Prioritises live web search for current information. */
    WEB,
    /** Plain conversation using the model's general knowledge plus memory; no retrieval tools. */
    CHAT
}
