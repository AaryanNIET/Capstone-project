package com.capstone.agenticrag.search;

import java.util.List;

/** A web-search backend. Implementations call the provider's documented HTTP API only. */
public interface LiveSearchProvider {

    /** Identifier used in configuration (SEARCH_PROVIDER). */
    String id();

    /** Whether the required configuration (API key, base URL) is present. */
    boolean isConfigured();

    List<SearchResult> search(String query, int maxResults) throws SearchProviderException;

    class SearchProviderException extends Exception {
        private final boolean transientFailure;
        private final long retryAfterMillis;

        public SearchProviderException(String message, boolean transientFailure, long retryAfterMillis) {
            super(message);
            this.transientFailure = transientFailure;
            this.retryAfterMillis = retryAfterMillis;
        }

        public boolean isTransient() {
            return transientFailure;
        }

        public long retryAfterMillis() {
            return retryAfterMillis;
        }
    }
}
