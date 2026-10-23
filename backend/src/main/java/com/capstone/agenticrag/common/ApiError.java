package com.capstone.agenticrag.common;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The single error envelope returned by every endpoint. Never contains stack traces or internal details. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status, String code, String message, String path, Instant timestamp,
                       Map<String, String> fieldErrors) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(status, code, message, path, Instant.now(), Map.of());
    }
}
