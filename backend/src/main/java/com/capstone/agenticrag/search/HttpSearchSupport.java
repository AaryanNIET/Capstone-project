package com.capstone.agenticrag.search;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

import org.jsoup.Jsoup;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.capstone.agenticrag.search.LiveSearchProvider.SearchProviderException;

/** Shared HTTP plumbing for search providers: timeouts and mapping of HTTP failures to transient/permanent errors. */
final class HttpSearchSupport {

    private HttpSearchSupport() {}

    static RestClient client(Duration timeout) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", "AgenticRagAssistant/1.0").build();
    }

    static <T> T call(String provider, Supplier<T> request) throws SearchProviderException {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            long retryAfter = parseRetryAfter(e.getResponseHeaders() == null ? null
                    : e.getResponseHeaders().getFirst("Retry-After"));
            if (status.value() == 429) {
                throw new SearchProviderException(provider + " rate limit reached", true, retryAfter);
            }
            if (status.value() == 401 || status.value() == 403) {
                throw new SearchProviderException(provider + " rejected the API key", false, 0);
            }
            throw new SearchProviderException(provider + " returned HTTP " + status.value(), status.is5xxServerError(), 0);
        } catch (ResourceAccessException e) {
            throw new SearchProviderException(provider + " could not be reached (timeout or network error)", true, 0);
        } catch (RuntimeException e) {
            throw new SearchProviderException(provider + " returned an unexpected response", false, 0);
        }
    }

    static String plain(String html) {
        return html == null ? null : Jsoup.parse(html).text();
    }

    private static long parseRetryAfter(String header) {
        if (header == null) {
            return 0;
        }
        try {
            return Math.min(5_000, Long.parseLong(header.trim()) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
