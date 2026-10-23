package com.capstone.agenticrag.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class CitationSanitizerTest {

    @Test
    void keepsValidCitationsAndRecordsThem() {
        Set<Integer> cited = new TreeSet<>();
        String out = AgentOrchestrator.sanitizeCitations("Paris is the capital [1] and is large [2].", Set.of(1, 2), cited);
        assertThat(out).isEqualTo("Paris is the capital [1] and is large [2].");
        assertThat(cited).containsExactly(1, 2);
    }

    @Test
    void removesFabricatedCitationNumbers() {
        Set<Integer> cited = new TreeSet<>();
        String out = AgentOrchestrator.sanitizeCitations("Claim [1][7] and another [9].", Set.of(1), cited);
        assertThat(out).isEqualTo("Claim [1] and another .");
        assertThat(cited).containsExactly(1);
    }

    @Test
    void splitsGroupedCitationsAndDropsInvalidMembers() {
        Set<Integer> cited = new TreeSet<>();
        String out = AgentOrchestrator.sanitizeCitations("See [1, 3, 4].", Set.of(1, 4), cited);
        assertThat(out).isEqualTo("See [1][4].");
        assertThat(cited).containsExactly(1, 4);
    }

    @Test
    void leavesCodeUntouched() {
        Set<Integer> cited = new TreeSet<>();
        String content = "Use `arr[1]` here:\n```java\nint x = arr[2];\n```\nDone [5].";
        String out = AgentOrchestrator.sanitizeCitations(content, Set.of(), cited);
        assertThat(out).isEqualTo("Use `arr[1]` here:\n```java\nint x = arr[2];\n```\nDone .");
        assertThat(cited).isEmpty();
    }
}
