package com.capstone.agenticrag.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Audit record of one tool invocation (stored in agent_executions.tool_calls). Arguments are truncated. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallRecord(String tool, String arguments, Status status, long durationMs, String error,
                             int iteration) {

    public enum Status { OK, ERROR, REJECTED, TIMEOUT }
}
