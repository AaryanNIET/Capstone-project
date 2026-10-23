package com.capstone.agenticrag.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.capstone.agenticrag.rag.HashingEmbeddingModel;

/**
 * Builds Spring AI models for an OpenAI-compatible endpoint (OpenAI, Groq, OpenRouter, LM Studio, vLLM, Ollama's
 * /v1 API). Keys come only from configuration/environment and are never exposed through the API.
 */
@Component
public class DefaultAiModels implements AiModels {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiModels.class);

    private final Optional<ChatModel> chat;
    private final Optional<EmbeddingModel> embedding;
    private final String chatModelName;
    private final String embeddingModelName;
    private final int embeddingDimensions;

    public DefaultAiModels(AppProperties props, ObservationRegistry observations) {
        AppProperties.Ai ai = props.ai();
        AppProperties.Embedding emb = ai.embedding();
        RetryTemplate retry = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(Duration.ofMillis(500), 2.0, Duration.ofSeconds(4))
                .retryOn(TransientAiException.class)
                .retryOn(ResourceAccessException.class)
                .build();

        this.chatModelName = ai.model();
        this.embeddingModelName = emb.model();
        this.embeddingDimensions = emb.dimensions();

        if (ai.chatConfigured()) {
            OpenAiApi api = api(ai.baseUrl(), keyOrPlaceholder(ai.apiKey()), ai.requestTimeout());
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    .model(ai.model())
                    .temperature(ai.temperature())
                    .maxTokens(ai.maxOutputTokens());
            if ("openai".equalsIgnoreCase(ai.provider()) && ai.baseUrl().contains("api.openai.com")) {
                options.streamUsage(true);
            }
            this.chat = Optional.of(OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options.build())
                    .toolCallingManager(ToolCallingManager.builder().observationRegistry(observations).build())
                    .retryTemplate(retry)
                    .observationRegistry(observations)
                    .build());
            log.info("Chat model configured: provider={} model={}", ai.provider(), ai.model());
        } else {
            this.chat = Optional.empty();
            log.warn("No chat model configured (set AI_PROVIDER and AI_API_KEY). Chat features are disabled.");
        }

        this.embedding = Optional.ofNullable(buildEmbedding(ai, emb, retry, observations));
        if (embedding.isEmpty()) {
            log.warn("No embedding model configured. Document ingestion and semantic memory search are disabled.");
        }
    }

    private static EmbeddingModel buildEmbedding(AppProperties.Ai ai, AppProperties.Embedding emb, RetryTemplate retry,
                                                 ObservationRegistry observations) {
        String provider = emb.provider() == null ? "none" : emb.provider().toLowerCase();
        switch (provider) {
            case "hash" -> {
                log.warn("Using the offline 'hash' embedding provider: lexical similarity only, not semantic. Demo use only.");
                return new HashingEmbeddingModel(emb.dimensions());
            }
            case "openai", "ollama" -> {
                String baseUrl = StringUtils.hasText(emb.baseUrl()) ? emb.baseUrl() : ai.baseUrl();
                String key = StringUtils.hasText(emb.apiKey()) ? emb.apiKey() : ai.apiKey();
                if (!"ollama".equals(provider) && !StringUtils.hasText(key)) {
                    return null;
                }
                OpenAiEmbeddingOptions.Builder options = OpenAiEmbeddingOptions.builder().model(emb.model());
                if (emb.model() != null && emb.model().startsWith("text-embedding-3")) {
                    options.dimensions(emb.dimensions());
                }
                log.info("Embedding model configured: provider={} model={} dimensions={}", provider, emb.model(),
                        emb.dimensions());
                return new OpenAiEmbeddingModel(api(baseUrl, keyOrPlaceholder(key), ai.requestTimeout()),
                        MetadataMode.EMBED, options.build(), retry, observations);
            }
            default -> {
                return null;
            }
        }
    }

    private static OpenAiApi api(String baseUrl, String apiKey, Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(timeout);
        reactor.netty.http.client.HttpClient reactive = reactor.netty.http.client.HttpClient.create()
                .responseTimeout(timeout);
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .webClientBuilder(WebClient.builder().clientConnector(new ReactorClientHttpConnector(reactive)))
                .responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
                .build();
    }

    /** Local servers such as Ollama ignore the key, but the client requires a non-empty value. */
    private static String keyOrPlaceholder(String key) {
        return StringUtils.hasText(key) ? key : "not-required";
    }

    @Override
    public Optional<ChatModel> chat() {
        return chat;
    }

    @Override
    public Optional<EmbeddingModel> embedding() {
        return embedding;
    }

    @Override
    public String chatModelName() {
        return chatModelName;
    }

    @Override
    public String embeddingModelName() {
        return embeddingModelName;
    }

    @Override
    public int embeddingDimensions() {
        return embeddingDimensions;
    }
}
