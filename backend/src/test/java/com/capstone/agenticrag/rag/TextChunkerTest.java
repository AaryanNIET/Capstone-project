package com.capstone.agenticrag.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.capstone.agenticrag.document.TextExtractor.Section;

class TextChunkerTest {

    @Test
    void shortTextIsOneChunkWithPageAndSection() {
        List<TextChunker.Chunk> chunks = new TextChunker(200, 20).chunk(List.of(new Section(3, "Intro", "Hello world.")));
        assertThat(chunks).containsExactly(new TextChunker.Chunk(0, 3, "Intro", "Hello world."));
    }

    @Test
    void longTextIsSplitWithinSizeAndIndexedAcrossSections() {
        String sentence = "This is sentence number %d of the test document. ";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            text.append(sentence.formatted(i));
        }
        TextChunker chunker = new TextChunker(300, 50);
        List<TextChunker.Chunk> chunks = chunker.chunk(List.of(new Section(1, null, text.toString()),
                new Section(2, null, "Second page.")));

        assertThat(chunks.size()).isGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(300));
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
        assertThat(chunks.getLast().pageNumber()).isEqualTo(2);
        // Consecutive chunks overlap so a sentence cut at a boundary is still retrievable.
        String first = chunks.get(0).text();
        String second = chunks.get(1).text();
        assertThat(first).contains(second.substring(0, 20));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new TextChunker(50, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(200, 150)).isInstanceOf(IllegalArgumentException.class);
    }
}
