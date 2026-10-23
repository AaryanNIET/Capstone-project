package com.capstone.agenticrag.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class MemoryPolicyTest {

    private final MemoryPolicy policy = new MemoryPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "My password is hunter2",
            "my api key: sk-abcdefghijklmnopqrstuvwx",
            "AKIAABCDEFGHIJKLMNOP is the key",
            "card 4111 1111 1111 1111",
            "SSN 123-45-6789",
            "-----BEGIN RSA PRIVATE KEY-----"})
    void rejectsSecretsEvenWhenExplicit(String content) {
        assertThat(policy.evaluate(content, true)).isEqualTo(MemoryPolicy.Decision.REJECT_SECRET);
    }

    @Test
    void sensitiveDataNeedsAnExplicitRequest() {
        String text = "User was diagnosed with asthma";
        assertThat(policy.evaluate(text, false)).isEqualTo(MemoryPolicy.Decision.REJECT_SENSITIVE);
        assertThat(policy.evaluate(text, true)).isEqualTo(MemoryPolicy.Decision.ALLOW);
    }

    @Test
    void allowsOrdinaryPreferencesAndRejectsInvalidLength() {
        assertThat(policy.evaluate("User prefers concise answers", false)).isEqualTo(MemoryPolicy.Decision.ALLOW);
        assertThat(policy.evaluate("ok", true)).isEqualTo(MemoryPolicy.Decision.REJECT_INVALID);
        assertThat(policy.evaluate("x".repeat(1001), true)).isEqualTo(MemoryPolicy.Decision.REJECT_INVALID);
    }

    @Test
    void nonLuhnDigitRunsAreNotTreatedAsCards() {
        assertThat(policy.containsSecret("Order 1234 5678 9012 3456 shipped")).isFalse();
    }
}
