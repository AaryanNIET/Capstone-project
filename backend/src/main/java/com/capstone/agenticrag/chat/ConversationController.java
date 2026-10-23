package com.capstone.agenticrag.chat;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.chat.ChatDtos.ConversationDetail;
import com.capstone.agenticrag.chat.ChatDtos.ConversationItem;
import com.capstone.agenticrag.common.PageResponse;
import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversations")
public class ConversationController {

    private final ConversationService conversations;

    public ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    @GetMapping
    @Operation(summary = "List the user's conversations, newest first; q searches titles and message text")
    public PageResponse<ConversationItem> list(@CurrentUser UserPrincipal me,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "false") boolean archived,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "30") int size) {
        return conversations.list(me.id(), q, archived, page, size);
    }

    @PostMapping
    @Operation(summary = "Create a conversation")
    public ResponseEntity<ConversationItem> create(@CurrentUser UserPrincipal me,
                                                      @Valid @RequestBody(required = false) ChatDtos.CreateConversationRequest req) {
        Conversation c = req == null ? conversations.create(me.id(), null, null)
                : conversations.create(me.id(), req.title(), req.mode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationItem.from(c));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a conversation with all persisted messages and their sources")
    public ConversationDetail get(@CurrentUser UserPrincipal me, @PathVariable UUID id) {
        return conversations.detail(me.id(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename, archive/unarchive or change the mode of a conversation")
    public ConversationItem update(@CurrentUser UserPrincipal me, @PathVariable UUID id,
                                      @Valid @RequestBody ChatDtos.UpdateConversationRequest req) {
        return ConversationItem.from(conversations.update(me.id(), id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation (memory retention follows the user's memory settings)")
    public ResponseEntity<Void> delete(@CurrentUser UserPrincipal me, @PathVariable UUID id) {
        conversations.delete(me.id(), id);
        return ResponseEntity.noContent().build();
    }
}
