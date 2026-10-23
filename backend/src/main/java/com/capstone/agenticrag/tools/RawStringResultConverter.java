package com.capstone.agenticrag.tools;

import java.lang.reflect.Type;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

/** Tool methods already return JSON strings; pass them through instead of JSON-encoding them a second time. */
public class RawStringResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return result == null ? "{\"status\":\"ok\"}" : result.toString();
    }
}
