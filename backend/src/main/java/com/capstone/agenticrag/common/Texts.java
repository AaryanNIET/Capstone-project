package com.capstone.agenticrag.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Small text helpers shared by ingestion, memory and search. */
public final class Texts {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    public static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "and", "or", "but", "of", "to", "in",
            "on", "for", "with", "is", "are", "was", "were", "be", "been", "am", "i", "my", "me", "you", "your",
            "it", "this", "that", "at", "as", "by", "from", "so", "do", "does", "please", "remember", "should",
            "would", "can", "could", "will", "about", "we", "our", "us", "they", "them", "their", "user", "what",
            "which", "who", "how", "when", "where", "why", "has", "have", "had", "not", "no", "yes", "also");

    private Texts() {}

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** Removes control characters (except newline/tab) and collapses runs of blank space. */
    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ');
        text = CONTROL.matcher(text).replaceAll(" ");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll(" *\\n *", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    public static String normalizeForComparison(String value) {
        return NON_WORD.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    public static Set<String> contentWords(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalizeForComparison(value).split(" "))
                .filter(w -> w.length() > 1 && !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    /** pgvector literal, e.g. {@code [0.1,0.2]}. */
    public static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
