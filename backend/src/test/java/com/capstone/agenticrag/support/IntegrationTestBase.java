package com.capstone.agenticrag.support;

import java.util.Optional;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.agenticrag.config.AiModels;
import com.capstone.agenticrag.rag.HashingEmbeddingModel;

/**
 * Boots the full application against a real pgvector database (Testcontainers) with a scripted chat model and the
 * offline hash embedding model. The container is shared by every integration test class in the JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestBase.FakeAi.class)
public abstract class IntegrationTestBase {

    protected static final int DIMENSIONS = 256;

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.flyway.placeholders.embedding_dimensions", () -> DIMENSIONS);
        r.add("app.ai.embedding.dimensions", () -> DIMENSIONS);
        r.add("app.ai.embedding.provider", () -> "hash");
        r.add("app.ai.provider", () -> "none");
        r.add("app.search.provider", () -> "none");
        r.add("app.storage.directory", () -> System.getProperty("java.io.tmpdir") + "/ara-test-uploads");
        r.add("app.rate-limit.chat-per-minute", () -> 1000);
        r.add("app.rate-limit.upload-per-minute", () -> 1000);
        r.add("app.rate-limit.auth-per-minute", () -> 1000);
        r.add("app.admin-emails", () -> "admin@example.com");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeChatModel chatModel;

    @TestConfiguration
    static class FakeAi {

        @Bean
        FakeChatModel fakeChatModel() {
            return new FakeChatModel();
        }

        @Bean
        @Primary
        AiModels testAiModels(FakeChatModel chat) {
            EmbeddingModel embedding = new HashingEmbeddingModel(DIMENSIONS);
            return new AiModels() {
                public Optional<ChatModel> chat() { return Optional.of(chat); }
                public Optional<EmbeddingModel> embedding() { return Optional.of(embedding); }
                public String chatModelName() { return "fake-model"; }
                public String embeddingModelName() { return "hash"; }
                public int embeddingDimensions() { return DIMENSIONS; }
            };
        }
    }
}
