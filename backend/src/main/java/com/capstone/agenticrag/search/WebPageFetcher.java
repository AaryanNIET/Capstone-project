package com.capstone.agenticrag.search;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.capstone.agenticrag.common.Texts;

/**
 * Fetches the readable text of a public web page for the agent. SSRF defences: http(s) only, default ports, every hop
 * (including redirects, followed manually) must resolve to public IPs, response size and time are capped, and only
 * HTML/plain text is accepted. Pages behind logins or paywalls are not bypassed: whatever the server returns
 * anonymously is all that is read. Fetched text is treated as untrusted data by the agent.
 */
@Component
public class WebPageFetcher {

    private static final int MAX_BYTES = 1_500_000;
    private static final int MAX_REDIRECTS = 3;

    public record Page(String url, String title, String text) {}

    public static class FetchException extends Exception {
        public FetchException(String message) {
            super(message);
        }
    }

    private final UrlSafety urls;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebPageFetcher(UrlSafety urls) {
        this.urls = urls;
    }

    public Page fetch(String url, int maxChars) throws FetchException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!urls.isSafeToFetch(current)) {
                throw new FetchException("This URL is not allowed (only public http/https pages can be read).");
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(current))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "AgenticRagAssistant/1.0 (+read-only)")
                    .header("Accept", "text/html,text/plain;q=0.9")
                    .GET().build();
            try {
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    response.body().close();
                    if (location == null) {
                        throw new FetchException("The page redirected without a location.");
                    }
                    current = URI.create(current).resolve(location).toString();
                    continue;
                }
                if (status == 401 || status == 403 || status == 402) {
                    response.body().close();
                    throw new FetchException("The page requires authorization or a subscription; it was not accessed.");
                }
                if (status >= 400) {
                    response.body().close();
                    throw new FetchException("The page returned HTTP " + status + ".");
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (!contentType.contains("text/html") && !contentType.contains("text/plain")) {
                    response.body().close();
                    throw new FetchException("Only HTML and plain-text pages can be read.");
                }
                byte[] body;
                try (InputStream in = response.body()) {
                    body = in.readNBytes(MAX_BYTES);
                }
                String raw = new String(body, StandardCharsets.UTF_8);
                if (contentType.contains("text/plain")) {
                    return new Page(current, current, Texts.truncate(Texts.clean(raw), maxChars));
                }
                Document doc = Jsoup.parse(raw, current);
                doc.select("script, style, noscript, iframe, svg, nav, footer, form, header").remove();
                return new Page(current, Texts.truncate(doc.title(), 300), Texts.truncate(Texts.clean(doc.body() == null ? "" : doc.body().wholeText()), maxChars));
            } catch (IOException e) {
                throw new FetchException("The page could not be retrieved (network error or timeout).");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("The request was cancelled.");
            } catch (IllegalArgumentException e) {
                throw new FetchException("Invalid URL.");
            }
        }
        throw new FetchException("Too many redirects.");
    }
}
