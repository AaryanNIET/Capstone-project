package com.capstone.agenticrag.agent;

import java.util.List;

public record AgentResult(String content, Status status, String errorCode, String errorMessage, int iterations,
                          List<ToolCallRecord> toolCalls, List<RunSource> sources, boolean usedDocuments,
                          boolean usedWeb, boolean usedMemory, Integer promptTokens, Integer completionTokens) {

    public enum Status { COMPLETED, CANCELLED, FAILED, TIMED_OUT }
}
