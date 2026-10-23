package com.capstone.agenticrag.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Typed view of the {@code app.*} configuration tree. Every value can be overridden by an environment variable. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendBaseUrl,
        Cors cors,
        List<String> adminEmails,
        Storage storage,
        Ai ai,
        Agent agent,
        Rag rag,
        Memory memory,
        Search search,
        RateLimit rateLimit,
        Mail mail) {

    public record Cors(List<String> allowedOrigins) {}

    public record Storage(String directory) {}

    public record Ai(String provider, String baseUrl, String apiKey, String model, double temperature,
                     int maxOutputTokens, Duration requestTimeout, Embedding embedding) {

        /** Chat is usable when a provider is selected and, for hosted providers, an API key is present. */
        public boolean chatConfigured() {
            if (!StringUtils.hasText(provider) || "none".equalsIgnoreCase(provider) || !StringUtils.hasText(model)) {
                return false;
            }
            return "ollama".equalsIgnoreCase(provider) || StringUtils.hasText(apiKey);
        }
    }

    public record Embedding(String provider, String baseUrl, String apiKey, String model, int dimensions) {}

    public record Agent(int maxIterations, int maxToolCalls, Duration toolTimeout, Duration maxDuration,
                        int recentMessages, int summaryTriggerMessages, int maxToolResultChars) {}

    public record Rag(int chunkSize, int chunkOverlap, int topK, double similarityThreshold, int maxChunksPerDocument) {}

    public record Memory(int topK, double similarityThreshold, double duplicateThreshold, boolean autoExtract,
                         int maxPerUser) {}

    public record Search(String provider, String apiKey, String baseUrl, int maxResults, Duration timeout,
                         Duration cacheTtl, int maxRetries) {}

    public record RateLimit(int chatPerMinute, int uploadPerMinute, int authPerMinute) {}

    public record Mail(String from, Duration resetTokenTtl) {}
}
