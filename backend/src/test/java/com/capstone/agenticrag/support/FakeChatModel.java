package com.capstone.agenticrag.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

/**
 * Deterministic stand-in for an LLM. If the user asks about "documents" and searchDocuments is offered, it requests
 * that tool; after a tool response it answers citing [1] (plus a fabricated [42] that the server must strip).
 * Otherwise it streams an echo of the user message in a few chunks. Records every prompt it receives.
 */
public class FakeChatModel implements ChatModel {

    public final List<Prompt> prompts = Collections.synchronizedList(new ArrayList<>());

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage("Summary of the conversation so far."))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        prompts.add(prompt);
        List<Message> messages = prompt.getInstructions();
        Message last = messages.getLast();

        if (last instanceof ToolResponseMessage tool) {
            String data = tool.getResponses().getFirst().responseData();
            String answer = data.contains("\"citation\":1")
                    ? "According to your document, the launch code name is BLUEBIRD [1]. Also see [42]."
                    : "I could not find that in your documents.";
            return chunks(answer);
        }
        // Trailing system messages (e.g. "no more tool calls") come after the user message.
        String user = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() == MessageType.USER) {
                user = messages.get(i).getText();
                break;
            }
        }
        boolean toolsOffered = prompt.getOptions() instanceof ToolCallingChatOptions o && o.getToolCallbacks() != null
                && o.getToolCallbacks().stream().anyMatch(cb -> cb.getToolDefinition().name().equals("searchDocuments"));
        boolean finalTurn = messages.stream().anyMatch(m -> m instanceof SystemMessage s
                && s.getText().startsWith("No more tool calls"));
        if (toolsOffered && !finalTurn && user.toLowerCase(Locale.ROOT).contains("documents")) {
            AssistantMessage call = AssistantMessage.builder()
                    .content("Let me check.")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "searchDocuments",
                            "{\"query\":\"launch code name\"}")))
                    .build();
            return Flux.just(new ChatResponse(List.of(new Generation(call))));
        }
        return chunks("You said: " + user);
    }

    /** The system prompt of the most recent streamed request. */
    public String lastSystemPrompt() {
        synchronized (prompts) {
            for (int i = prompts.size() - 1; i >= 0; i--) {
                List<Message> m = prompts.get(i).getInstructions();
                if (!m.isEmpty() && m.getFirst() instanceof SystemMessage s && !(m.getLast() instanceof ToolResponseMessage)
                        && m.stream().anyMatch(UserMessage.class::isInstance)) {
                    return s.getText();
                }
            }
        }
        return "";
    }

    private static Flux<ChatResponse> chunks(String text) {
        List<ChatResponse> parts = new ArrayList<>();
        int step = Math.max(1, text.length() / 3);
        for (int i = 0; i < text.length(); i += step) {
            parts.add(new ChatResponse(List.of(new Generation(
                    new AssistantMessage(text.substring(i, Math.min(text.length(), i + step)))))));
        }
        return Flux.fromIterable(parts);
    }
}
