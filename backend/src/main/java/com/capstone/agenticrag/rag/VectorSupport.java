package com.capstone.agenticrag.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Detects pgvector capabilities once. pgvector 0.8+ supports iterative HNSW scans, which keep filtered queries
 * (WHERE user_id = ?) from returning too few rows when many users share the index.
 */
@Component
public class VectorSupport {

    private static final Logger log = LoggerFactory.getLogger(VectorSupport.class);

    private final JdbcTemplate jdbc;
    private volatile Boolean iterativeScan;

    public VectorSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Must be called inside a transaction: applies per-transaction search settings. */
    public void applySearchSettings() {
        if (supportsIterativeScan()) {
            jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
        }
        jdbc.execute("SET LOCAL hnsw.ef_search = 100");
    }

    private boolean supportsIterativeScan() {
        Boolean cached = iterativeScan;
        if (cached == null) {
            String version = jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
            cached = version != null && compare(version, "0.8.0") >= 0;
            iterativeScan = cached;
            log.info("pgvector version {} (iterative scan {})", version, cached ? "enabled" : "unavailable");
        }
        return cached;
    }

    private static int compare(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? parse(pa[i]) : 0;
            int y = i < pb.length ? parse(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
