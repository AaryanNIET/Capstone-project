package com.capstone.agenticrag.source;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.chat.MessageRepository;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;

@RestController
@RequestMapping("/api/messages/{messageId}/sources")
@Tag(name = "Sources")
public class SourceController {

    private final MessageRepository messages;
    private final MessageSourceRepository sources;

    public SourceController(MessageRepository messages, MessageSourceRepository sources) {
        this.messages = messages;
        this.sources = sources;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Sources used for an assistant message, ordered by citation number")
    public List<SourceDto> list(@CurrentUser UserPrincipal me, @PathVariable UUID messageId) {
        messages.findByIdAndUserId(messageId, me.id()).orElseThrow(() -> ApiException.notFound("Message"));
        return sources.findByMessageIdOrderByCitationAsc(messageId).stream().map(SourceDto::from).toList();
    }
}
