package com.capstone.agenticrag.rag;

import java.util.ArrayList;
import java.util.List;

import com.capstone.agenticrag.document.TextExtractor.Section;

/**
 * Splits extracted sections into overlapping chunks of roughly {@code chunkSize} characters. Chunks never span two
 * sections, so each chunk keeps exactly one page number / heading. Breaks prefer paragraph, then sentence, then word
 * boundaries.
 */
public final class TextChunker {

    public record Chunk(int index, Integer pageNumber, String section, String text) {}

    private final int chunkSize;
    private final int overlap;

    public TextChunker(int chunkSize, int overlap) {
        if (chunkSize < 100) {
            throw new IllegalArgumentException("chunkSize must be at least 100 characters");
        }
        if (overlap < 0 || overlap >= chunkSize / 2) {
            throw new IllegalArgumentException("overlap must be between 0 and half of chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<Chunk> chunk(List<Section> sections) {
        List<Chunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            for (String piece : split(section.text())) {
                chunks.add(new Chunk(chunks.size(), section.pageNumber(), section.heading(), piece));
            }
        }
        return chunks;
    }

    List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        String t = text.strip();
        if (t.isEmpty()) {
            return pieces;
        }
        int start = 0;
        while (start < t.length()) {
            int end = Math.min(t.length(), start + chunkSize);
            if (end < t.length()) {
                end = findBreak(t, start, end);
            }
            String piece = t.substring(start, end).strip();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= t.length()) {
                break;
            }
            int next = end - overlap;
            // Start the overlap at a word boundary and always make forward progress.
            while (next > start && next < end && !Character.isWhitespace(t.charAt(next - 1))) {
                next++;
            }
            start = Math.max(next, start + 1);
        }
        return pieces;
    }

    private int findBreak(String t, int start, int end) {
        int min = start + chunkSize / 2;
        int paragraph = t.lastIndexOf("\n\n", end);
        if (paragraph >= min) {
            return paragraph;
        }
        for (int i = end - 1; i >= min; i--) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && i + 1 < t.length()
                    && Character.isWhitespace(t.charAt(i + 1))) {
                return i + 1;
            }
        }
        int space = t.lastIndexOf(' ', end);
        return space >= min ? space : end;
    }
}
