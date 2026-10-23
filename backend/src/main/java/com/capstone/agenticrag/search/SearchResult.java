package com.capstone.agenticrag.search;

import java.time.Instant;

/** One web result exactly as returned by the provider (after sanitisation). Nothing is synthesised. */
public record SearchResult(String title, String url, String snippet, String publishedAt, Instant retrievedAt) {}
