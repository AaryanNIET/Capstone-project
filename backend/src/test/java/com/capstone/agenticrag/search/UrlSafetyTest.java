package com.capstone.agenticrag.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlSafetyTest {

    private final UrlSafety urls = new UrlSafety();

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,hi", "file:///etc/passwd",
            "https://user:pass@example.com/", "ftp://example.com/"})
    void rejectsUnsafeLinks(String raw) {
        assertThat(urls.sanitizeLink(raw)).isEmpty();
    }

    @Test
    void acceptsHttpLinks() {
        assertThat(urls.sanitizeLink("https://example.com/a/../b")).contains("https://example.com/b");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost/", "http://127.0.0.1/", "http://10.0.0.5/", "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data/", "http://[::1]/", "http://100.64.0.1/",
            "http://example.com:8080/", "http://printer.local/", "http://metadata.google.internal/"})
    void blocksInternalTargets(String raw) {
        assertThat(urls.isSafeToFetch(raw)).isFalse();
    }

    @Test
    void publicAddressesArePublic() throws Exception {
        assertThat(UrlSafety.isPublic(InetAddress.getByName("93.184.216.34"))).isTrue();
        assertThat(UrlSafety.isPublic(InetAddress.getByName("172.16.0.1"))).isFalse();
    }

    @Test
    void dedupeKeyIgnoresWwwTrailingSlashAndTracking() {
        assertThat(urls.dedupeKey("https://www.Example.com/news/?utm_source=x"))
                .isEqualTo(urls.dedupeKey("https://example.com/news"));
    }
}
