package com.capstone.agenticrag.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryCandidateExtractorTest {

    private final MemoryCandidateExtractor extractor = new MemoryCandidateExtractor();

    @Test
    void extractsNameAsExclusiveFact() {
        List<MemoryCandidateExtractor.Candidate> c = extractor.extract("Hi! My name is Priya.");
        assertThat(c).hasSize(1);
        assertThat(c.getFirst().content()).isEqualTo("User's name is Priya");
        assertThat(c.getFirst().category()).isEqualTo(Memory.Category.FACT);
        assertThat(c.getFirst().exclusivePrefix()).isEqualTo("User's name is ");
        assertThat(c.getFirst().explicit()).isFalse();
    }

    @Test
    void ignoresLeadingGreetings() {
        assertThat(extractor.extract("Hello, my name is Sam."))
                .extracting(MemoryCandidateExtractor.Candidate::content).containsExactly("User's name is Sam");
        assertThat(extractor.extract("Hey there! Ok so I prefer dark mode."))
                .extracting(MemoryCandidateExtractor.Candidate::content).containsExactly("User prefers dark mode");
    }

    @Test
    void explicitRememberRequestIsExplicitAndThirdPerson() {
        List<MemoryCandidateExtractor.Candidate> c = extractor.extract("Remember that I prefer concise answers.");
        assertThat(c).hasSize(1);
        assertThat(c.getFirst().explicit()).isTrue();
        assertThat(c.getFirst().content()).isEqualTo("User prefers concise answers");
        assertThat(c.getFirst().category()).isEqualTo(Memory.Category.PREFERENCE);
    }

    @Test
    void standingInstructionsBecomeInstructionMemories() {
        List<MemoryCandidateExtractor.Candidate> c = extractor.extract("From now on, answer in British English.");
        assertThat(c).hasSize(1);
        assertThat(c.getFirst().category()).isEqualTo(Memory.Category.INSTRUCTION);
    }

    @Test
    void ignoresQuestionsAndPlainRequests() {
        assertThat(extractor.extract("Do I prefer tea or coffee?")).isEmpty();
        assertThat(extractor.extract("Summarize the attached document for me.")).isEmpty();
    }

    @Test
    void extractsProjectStatements() {
        List<MemoryCandidateExtractor.Candidate> c = extractor.extract("I'm working on a capstone RAG assistant in Java.");
        assertThat(c).extracting(MemoryCandidateExtractor.Candidate::category).containsExactly(Memory.Category.PROJECT);
        assertThat(c.getFirst().content()).startsWith("User is working on a capstone RAG assistant");
    }
}
