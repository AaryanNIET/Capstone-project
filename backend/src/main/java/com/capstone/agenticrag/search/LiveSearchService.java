package com.capstone.agenticrag.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.search.LiveSearchProvider.SearchProviderException;

/**
 * Executes live web searches through the configured provider with caching, bounded retries for transient failures
 * (timeouts, 5xx, 429), result sanitisation and de-duplication. It never fabricates results: when the provider is
 * missing or failing, callers receive an explicit unavailable/failed status.
 */
@Service
public class LiveSearchService {

    private static final Logger log = LoggerFactory.getLogger(LiveSearchService.class);
    public static final int MAX_QUERY_LENGTH = 400;

    public record SearchResponse(String query, String provider, List<SearchResult> results, boolean cached,
                                 Instant retrievedAt) {}

    public static class LiveSearchException extends Exception {
        private final boolean unavailable;

        LiveSearchException(String message, boolean unavailable) {
            super(message);
            this.unavailable = unavailable;
        }

        /** True when search is not configured at all (as opposed to a failed call). */
        public boolean isUnavailable() {
            return unavailable;
        }
    }

    private final AppProperties.Search config;
    private final Optional<LiveSearchProvider> provider;
    private final UrlSafety urls;
    private final Cache<String, SearchResponse> cache;

    public LiveSearchService(AppProperties props, List<LiveSearchProvider> providers, UrlSafety urls) {
        this.config = props.search();
        this.urls = urls;
        String selected = config.provider() == null ? "none" : config.provider().toLowerCase(Locale.ROOT);
        Map<String, LiveSearchProvider> byId = providers.stream()
                .collect(Collectors.toMap(LiveSearchProvider::id, Function.identity()));
        this.provider = Optional.ofNullable(byId.get(selected)).filter(LiveSearchProvider::isConfigured);
        this.cache = Caffeine.newBuilder().expireAfterWrite(config.cacheTtl()).maximumSize(1_000).build();
        if (provider.isPresent()) {
            log.info("Live search provider: {}", selected);
        } else if (!"none".equals(selected)) {
            log.warn("Live search provider '{}' is selected but not configured (missing SEARCH_API_KEY/SEARCH_BASE_URL)", selected);
        } else {
            log.warn("Live search is disabled (SEARCH_PROVIDER=none). The assistant will say so instead of searching.");
        }
    }

    public boolean isAvailable() {
        return provider.isPresent();
    }

    public String providerName() {
        return provider.map(LiveSearchProvider::id).orElse("none");
    }

    public SearchResponse search(String rawQuery) throws LiveSearchException {
        LiveSearchProvider p = provider.orElseThrow(() -> new LiveSearchException(
                "Live web search is not configured on this server.", true));
        String query = Texts.truncate(Texts.clean(rawQuery == null ? "" : rawQuery).replace('\n', ' '), MAX_QUERY_LENGTH);
        if (query.isBlank()) {
            throw new LiveSearchException("The search query is empty.", false);
        }
        String key = query.toLowerCase(Locale.ROOT);
        SearchResponse cached = cache.getIfPresent(key);
        if (cached != null) {
            return new SearchResponse(cached.query(), cached.provider(), cached.results(), true, cached.retrievedAt());
        }

        int attempts = Math.max(1, config.maxRetries() + 1);
        SearchProviderException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                List<SearchResult> raw = p.search(query, config.maxResults() * 2);
                SearchResponse response = new SearchResponse(query, p.id(), clean(raw), false, Instant.now());
                cache.put(key, response);
                return response;
            } catch (SearchProviderException e) {
                last = e;
                if (!e.isTransient() || attempt == attempts) {
                    break;
                }
                sleep(Math.max(e.retryAfterMillis(), 400L * attempt));
            }
        }
        log.warn("Live search failed: {}", last.getMessage());
        throw new LiveSearchException("Live search failed: " + last.getMessage(), false);
    }

    private List<SearchResult> clean(List<SearchResult> raw) {
        List<SearchResult> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchResult r : raw) {
            Optional<String> url = urls.sanitizeLink(r.url());
            if (url.isEmpty() || !seen.add(urls.dedupeKey(url.get()))) {
                continue;
            }
            String title = Texts.truncate(Texts.clean(r.title()), 300);
            out.add(new SearchResult(title.isBlank() ? url.get() : title, url.get(),
                    Texts.truncate(Texts.clean(r.snippet()), 1000), r.publishedAt(), r.retrievedAt()));
            if (out.size() >= config.maxResults()) {
                break;
            }
        }
        return out;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
