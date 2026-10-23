package com.capstone.agenticrag.feedback;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.chat.Message;
import com.capstone.agenticrag.chat.MessageRepository;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;

@RestController
@RequestMapping("/api/messages/{messageId}/feedback")
@Tag(name = "Feedback")
public class FeedbackController {

    public record FeedbackRequest(@NotNull(message = "Rating is required") ResponseFeedback.Rating rating,
                                  @Size(max = 1000, message = "Comment must be at most 1000 characters") String comment) {}

    public record FeedbackResponse(UUID messageId, ResponseFeedback.Rating rating, String comment, Instant updatedAt) {
        static FeedbackResponse from(ResponseFeedback f) {
            return new FeedbackResponse(f.getMessageId(), f.getRating(), f.getComment(), f.getUpdatedAt());
        }
    }

    private final MessageRepository messages;
    private final ResponseFeedbackRepository feedback;

    public FeedbackController(MessageRepository messages, ResponseFeedbackRepository feedback) {
        this.messages = messages;
        this.feedback = feedback;
    }

    @PutMapping
    @Transactional
    @Operation(summary = "Rate an assistant message (thumbs up or down) with an optional comment")
    public FeedbackResponse rate(@CurrentUser UserPrincipal me, @PathVariable UUID messageId,
                                 @Valid @RequestBody FeedbackRequest req) {
        Message m = messages.findByIdAndUserId(messageId, me.id()).orElseThrow(() -> ApiException.notFound("Message"));
        if (m.getRole() != Message.Role.ASSISTANT) {
            throw ApiException.badRequest("Only assistant messages can be rated");
        }
        ResponseFeedback f = feedback.findByMessageIdAndUserId(messageId, me.id())
                .orElseGet(() -> new ResponseFeedback(messageId, me.id()));
        String comment = req.comment() == null || req.comment().isBlank() ? null : Texts.clean(req.comment());
        f.update(req.rating(), comment);
        return FeedbackResponse.from(feedback.save(f));
    }

    @DeleteMapping
    @Transactional
    @Operation(summary = "Remove your rating of an assistant message")
    public ResponseEntity<Void> remove(@CurrentUser UserPrincipal me, @PathVariable UUID messageId) {
        messages.findByIdAndUserId(messageId, me.id()).orElseThrow(() -> ApiException.notFound("Message"));
        feedback.findByMessageIdAndUserId(messageId, me.id()).ifPresent(feedback::delete);
        return ResponseEntity.noContent().build();
    }
}
