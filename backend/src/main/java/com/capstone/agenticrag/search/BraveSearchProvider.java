package com.capstone.agenticrag.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.capstone.agenticrag.config.AppProperties;

/** Brave Search API: GET /res/v1/web/search with the X-Subscription-Token header. */
@Component
public class BraveSearchProvider implements LiveSearchProvider {

    static final String DEFAULT_BASE_URL = "https://api.search.brave.com";

    private final AppProperties.Search config;
    private final RestClient http;
    private final String baseUrl;

    public BraveSearchProvider(AppProperties props) {
        this.config = props.search();
        this.http = HttpSearchSupport.client(config.timeout());
        this.baseUrl = StringUtils.hasText(config.baseUrl()) ? config.baseUrl() : DEFAULT_BASE_URL;
    }

    @Override
    public String id() {
        return "brave";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(config.apiKey());
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws SearchProviderException {
        var uri = UriComponentsBuilder.fromUriString(baseUrl + "/res/v1/web/search")
                .queryParam("q", query)
                .queryParam("count", Math.min(maxResults, 20))
                .queryParam("safesearch", "moderate")
                .encode().build().toUri();
        JsonNode body = HttpSearchSupport.call("Brave Search", () -> http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Subscription-Token", config.apiKey())
                .retrieve()
                .body(JsonNode.class));
        List<SearchResult> out = new ArrayList<>();
        Instant now = Instant.now();
        if (body != null) {
            for (JsonNode r : body.path("web").path("results")) {
                out.add(new SearchResult(HttpSearchSupport.plain(r.path("title").asText("")), r.path("url").asText(""),
                        HttpSearchSupport.plain(r.path("description").asText("")),
                        r.hasNonNull("page_age") ? r.get("page_age").asText() : (r.hasNonNull("age") ? r.get("age").asText() : null),
                        now));
            }
        }
        return out;
    }
}
