package com.capstone.agenticrag.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.config.AiModels;
import com.capstone.agenticrag.document.DocumentService;
import com.capstone.agenticrag.search.LiveSearchService;

/** Public feature flags so the UI can explain what is available (e.g. "live search is not configured"). */
@RestController
@RequestMapping("/api/system")
@Tag(name = "System")
public class SystemController {

    public record SystemStatus(boolean chatAvailable, String chatModel, boolean embeddingAvailable,
                               boolean webSearchAvailable, String searchProvider, int maxUploadMegabytes,
                               String version) {}

    private final AiModels models;
    private final LiveSearchService search;

    public SystemController(AiModels models, LiveSearchService search) {
        this.models = models;
        this.search = search;
    }

    @GetMapping("/status")
    @Operation(summary = "Which AI, retrieval and live-search features are configured on this server")
    public SystemStatus status() {
        return new SystemStatus(models.chatAvailable(), models.chatAvailable() ? models.chatModelName() : null,
                models.embeddingAvailable(), search.isAvailable(), search.providerName(),
                (int) (DocumentService.MAX_FILE_BYTES / (1024 * 1024)),
                getClass().getPackage().getImplementationVersion());
    }
}
