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

/**
 * Self-hosted SearXNG metasearch (free, no API key). Requires an instance with the JSON output format enabled
 * (settings.yml: search.formats includes json). SEARCH_BASE_URL is an operator setting, not user input.
 */
@Component
public class SearxngSearchProvider implements LiveSearchProvider {

    private final AppProperties.Search config;
    private final RestClient http;

    public SearxngSearchProvider(AppProperties props) {
        this.config = props.search();
        this.http = HttpSearchSupport.client(config.timeout());
    }

    @Override
    public String id() {
        return "searxng";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(config.baseUrl());
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws SearchProviderException {
        var uri = UriComponentsBuilder.fromUriString(config.baseUrl().replaceAll("/+$", "") + "/search")
                .queryParam("q", query)
                .queryParam("format", "json")
                .queryParam("safesearch", 1)
                .encode().build().toUri();
        JsonNode body = HttpSearchSupport.call("SearXNG", () -> http.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(JsonNode.class));
        List<SearchResult> out = new ArrayList<>();
        Instant now = Instant.now();
        if (body != null) {
            for (JsonNode r : body.path("results")) {
                if (out.size() >= maxResults) {
                    break;
                }
                out.add(new SearchResult(r.path("title").asText(""), r.path("url").asText(""),
                        HttpSearchSupport.plain(r.path("content").asText("")),
                        r.hasNonNull("publishedDate") ? r.get("publishedDate").asText() : null, now));
            }
        }
        return out;
    }
}
