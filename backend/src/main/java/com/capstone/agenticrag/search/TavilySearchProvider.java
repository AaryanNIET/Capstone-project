package com.capstone.agenticrag.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.capstone.agenticrag.config.AppProperties;

/** Tavily Search API: POST https://api.tavily.com/search with a Bearer API key. */
@Component
public class TavilySearchProvider implements LiveSearchProvider {

    static final String DEFAULT_BASE_URL = "https://api.tavily.com";

    private final AppProperties.Search config;
    private final RestClient http;
    private final String baseUrl;

    public TavilySearchProvider(AppProperties props) {
        this.config = props.search();
        this.http = HttpSearchSupport.client(config.timeout());
        this.baseUrl = StringUtils.hasText(config.baseUrl()) ? config.baseUrl() : DEFAULT_BASE_URL;
    }

    @Override
    public String id() {
        return "tavily";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(config.apiKey());
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws SearchProviderException {
        JsonNode body = HttpSearchSupport.call("Tavily", () -> http.post()
                .uri(baseUrl + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + config.apiKey())
                .body(Map.of("query", query, "max_results", maxResults, "search_depth", "basic",
                        "include_answer", false, "include_images", false))
                .retrieve()
                .body(JsonNode.class));
        List<SearchResult> out = new ArrayList<>();
        Instant now = Instant.now();
        if (body != null) {
            for (JsonNode r : body.path("results")) {
                out.add(new SearchResult(r.path("title").asText(""), r.path("url").asText(""),
                        HttpSearchSupport.plain(r.path("content").asText("")),
                        r.hasNonNull("published_date") ? r.get("published_date").asText() : null, now));
            }
        }
        return out;
    }
}
