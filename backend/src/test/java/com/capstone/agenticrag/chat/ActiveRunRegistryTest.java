package com.capstone.agenticrag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.capstone.agenticrag.agent.AgentRunContext;
import com.capstone.agenticrag.exception.ApiException;

class ActiveRunRegistryTest {

    private final ActiveRunRegistry registry = new ActiveRunRegistry();
    private final UUID owner = UUID.randomUUID();
    private final UUID conversation = UUID.randomUUID();

    @Test
    void allowsOnlyOneRunPerConversation() {
        ActiveRunRegistry.ActiveRun run = registry.reserve(conversation, owner);
        assertThatThrownBy(() -> registry.reserve(conversation, owner))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("RUN_IN_PROGRESS"));
        registry.release(run);
        assertThat(registry.isActive(conversation)).isFalse();
        registry.release(registry.reserve(conversation, owner));
    }

    @Test
    void onlyTheOwnerCanCancel() {
        ActiveRunRegistry.ActiveRun run = registry.reserve(conversation, owner);
        AgentRunContext ctx = context();
        run.attach(ctx);

        assertThat(registry.cancel(UUID.randomUUID(), conversation)).isFalse();
        assertThat(ctx.isCancelled()).isFalse();
        assertThat(registry.cancel(owner, conversation)).isTrue();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void cancelBeforeAttachIsAppliedOnAttach() {
        ActiveRunRegistry.ActiveRun run = registry.reserve(conversation, owner);
        assertThat(registry.cancel(owner, conversation)).isTrue();
        AgentRunContext ctx = context();
        run.attach(ctx);
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void releasingAStaleRunDoesNotRemoveTheCurrentOne() {
        ActiveRunRegistry.ActiveRun first = registry.reserve(conversation, owner);
        registry.release(first);
        registry.reserve(conversation, owner);
        registry.release(first);
        assertThat(registry.isActive(conversation)).isTrue();
    }

    private AgentRunContext context() {
        return new AgentRunContext(owner, conversation, AssistantMode.AUTO, "hi", true, Duration.ofSeconds(30));
    }
}
