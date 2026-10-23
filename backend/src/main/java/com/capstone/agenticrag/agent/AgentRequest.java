package com.capstone.agenticrag.agent;

import java.util.List;

import com.capstone.agenticrag.chat.Message;
import com.capstone.agenticrag.memory.Memory;

/**
 * Everything the orchestrator needs for one turn, assembled by the chat service from the current user's data only.
 *
 * @param history          recent messages of this conversation (chronological, excluding the new user message)
 * @param summary          rolling summary of older messages, or null
 * @param memories         long-term memories relevant to this message (empty when memory is disabled)
 * @param savedMemoryNotes memories saved from this very message, so the assistant can acknowledge them
 * @param readyDocuments   number of documents the user can search
 */
public record AgentRequest(AgentRunContext context, String userName, List<Message> history, String summary,
                           List<Memory> memories, List<String> savedMemoryNotes, long readyDocuments) {}
