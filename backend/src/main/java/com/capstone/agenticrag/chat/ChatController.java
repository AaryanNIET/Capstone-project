package com.capstone.agenticrag.chat;

import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.capstone.agenticrag.chat.ChatDtos.ChatResponse;
import com.capstone.agenticrag.chat.ChatDtos.SendMessageRequest;
import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;

@RestController
@RequestMapping("/api/chat/{conversationId}")
@Tag(name = "Chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping("/messages")
    @Operation(summary = "Send a message (or regenerate the last reply) and wait for the complete answer")
    public ChatResponse send(@CurrentUser UserPrincipal me, @PathVariable UUID conversationId,
                             @Valid @RequestBody SendMessageRequest req) {
        return chat.send(me.id(), conversationId, req);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send a message (or regenerate) and stream the answer as Server-Sent Events: "
            + "meta, status, tool, content, reset, sources, done, error")
    public SseEmitter stream(@CurrentUser UserPrincipal me, @PathVariable UUID conversationId,
                             @Valid @RequestBody SendMessageRequest req) {
        return chat.stream(me.id(), conversationId, req);
    }

    @PostMapping("/cancel")
    @Operation(summary = "Stop the response being generated in this conversation")
    public Map<String, Boolean> cancel(@CurrentUser UserPrincipal me, @PathVariable UUID conversationId) {
        return Map.of("cancelled", chat.cancel(me.id(), conversationId));
    }
}
