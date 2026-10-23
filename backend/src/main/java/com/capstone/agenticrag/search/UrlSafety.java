package com.capstone.agenticrag.search;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * URL validation for links shown to users and for server-side fetching (SSRF protection).
 */
@Component
public class UrlSafety {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern TRACKING_PARAM = Pattern.compile("(?i)^(utm_[a-z]+|fbclid|gclid|mc_eid|ref_src)=.*");
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "metadata.google.internal", "metadata");

    /**
     * Returns a normalised http(s) URL that is safe to render as a link, or empty. Rejects other schemes
     * (javascript:, data:, file:), embedded credentials and over-long URLs.
     */
    public Optional<String> sanitizeLink(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_URL_LENGTH) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(raw.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return Optional.empty();
            }
            if (uri.getRawUserInfo() != null || uri.getHost() == null) {
                return Optional.empty();
            }
            return Optional.of(uri.normalize().toString());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** Key used to detect duplicate results: lower-case host without www, path without trailing slash, no tracking params. */
    public String dedupeKey(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
            StringBuilder query = new StringBuilder();
            if (uri.getRawQuery() != null) {
                for (String p : uri.getRawQuery().split("&")) {
                    if (!TRACKING_PARAM.matcher(p).matches()) {
                        query.append(p).append('&');
                    }
                }
            }
            return host + path + "?" + query;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    /**
     * True only for http(s) URLs on default ports whose host resolves exclusively to public addresses. Blocks
     * loopback, private (RFC 1918), link-local (incl. cloud metadata 169.254.169.254), CGNAT, multicast,
     * unique-local IPv6 and unspecified addresses.
     */
    public boolean isSafeToFetch(String raw) {
        Optional<String> link = sanitizeLink(raw);
        if (link.isEmpty()) {
            return false;
        }
        URI uri = URI.create(link.get());
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return false;
        }
        String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal")) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress a : addresses) {
                if (!isPublic(a)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            if (first == 0 || first >= 240) return false;                 // 0.0.0.0/8, reserved
            if (first == 100 && second >= 64 && second <= 127) return false; // CGNAT 100.64.0.0/10
            if (first == 192 && second == 0 && (b[2] & 0xff) == 0) return false; // 192.0.0.0/24
            if (first == 198 && (second == 18 || second == 19)) return false;   // benchmarking
        } else if (a instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return false; // fc00::/7 unique local
            if (first == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) return false; // doc
            // IPv4-mapped (::ffff:a.b.c.d): re-check the embedded IPv4 address
            boolean mapped = true;
            for (int i = 0; i < 10; i++) mapped &= b[i] == 0;
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
