package com.capstone.agenticrag.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.agenticrag.chat.ChatDtos.ConversationDetail;
import com.capstone.agenticrag.chat.ChatDtos.ConversationItem;
import com.capstone.agenticrag.chat.ChatDtos.MessageDto;
import com.capstone.agenticrag.common.PageResponse;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.feedback.ResponseFeedback;
import com.capstone.agenticrag.feedback.ResponseFeedbackRepository;
import com.capstone.agenticrag.memory.MemoryService;
import com.capstone.agenticrag.source.MessageSource;
import com.capstone.agenticrag.source.MessageSourceRepository;
import com.capstone.agenticrag.source.SourceDto;
import com.capstone.agenticrag.user.User;
import com.capstone.agenticrag.user.UserService;

@Service
public class ConversationService {

    public static final String DEFAULT_TITLE = "New chat";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageSourceRepository sources;
    private final ResponseFeedbackRepository feedback;
    private final UserService users;
    private final MemoryService memories;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               MessageSourceRepository sources, ResponseFeedbackRepository feedback, UserService users,
                               MemoryService memories) {
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
        this.feedback = feedback;
        this.users = users;
        this.memories = memories;
    }

    /** Loads a conversation only if it belongs to the user; otherwise 404 so ids of other users are not revealed. */
    @Transactional(readOnly = true)
    public Conversation requireOwned(UUID userId, UUID conversationId) {
        return conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationItem> list(UUID userId, String query, boolean archived, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        Page<Conversation> result;
        if (query == null || query.isBlank()) {
            result = conversations.findByUserIdAndArchivedOrderByUpdatedAtDesc(userId, archived, pageable);
        } else {
            String escaped = query.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            result = conversations.search(userId, archived, "%" + Texts.truncate(escaped, 100) + "%", pageable);
        }
        return PageResponse.from(result, ConversationItem::from);
    }

    @Transactional
    public Conversation create(UUID userId, String title, AssistantMode mode) {
        User user = users.get(userId);
        String t = title == null || title.isBlank() ? DEFAULT_TITLE : title.trim();
        return conversations.save(new Conversation(userId, t, mode != null ? mode : user.getDefaultMode()));
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(UUID userId, UUID conversationId) {
        Conversation c = requireOwned(userId, conversationId);
        List<Message> list = messages.findByConversationIdOrderByCreatedAtAsc(c.getId());
        return new ConversationDetail(c.getId(), c.getTitle(), c.getMode(), c.isArchived(), c.getCreatedAt(),
                c.getUpdatedAt(), toDtos(userId, list));
    }

    @Transactional
    public Conversation update(UUID userId, UUID conversationId, ChatDtos.UpdateConversationRequest req) {
        Conversation c = requireOwned(userId, conversationId);
        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw ApiException.badRequest("Title must not be blank");
            }
            c.setTitle(req.title().trim());
        }
        if (req.archived() != null) {
            c.setArchived(req.archived());
        }
        if (req.mode() != null) {
            c.setMode(req.mode());
        }
        c.touch();
        return c;
    }

    /**
     * Deletes the conversation with its messages, sources, feedback, summary and agent executions (DB cascade).
     * Memories learned from it follow the user's retention setting: deleted when
     * {@code deleteMemoriesWithConversation} is on, otherwise kept with their provenance link cleared.
     */
    @Transactional
    public void delete(UUID userId, UUID conversationId) {
        Conversation c = requireOwned(userId, conversationId);
        User user = users.get(userId);
        if (user.isDeleteMemoriesWithConversation()) {
            memories.deleteBySourceConversation(userId, c.getId());
        }
        conversations.delete(c);
    }

    @Transactional(readOnly = true)
    public List<MessageDto> toDtos(UUID userId, List<Message> list) {
        List<UUID> assistantIds = list.stream().filter(m -> m.getRole() == Message.Role.ASSISTANT).map(Message::getId).toList();
        Map<UUID, List<SourceDto>> sourcesByMessage = assistantIds.isEmpty() ? Map.of()
                : sources.findByMessageIdInOrderByCitationAsc(assistantIds).stream()
                .collect(Collectors.groupingBy(MessageSource::getMessageId,
                        Collectors.mapping(SourceDto::from, Collectors.toList())));
        Map<UUID, ResponseFeedback.Rating> ratings = assistantIds.isEmpty() ? Map.of()
                : feedback.findByUserIdAndMessageIdIn(userId, assistantIds).stream()
                .collect(Collectors.toMap(ResponseFeedback::getMessageId, ResponseFeedback::getRating));
        return list.stream().map(m -> MessageDto.from(m,
                m.getRole() == Message.Role.ASSISTANT ? sourcesByMessage.getOrDefault(m.getId(), List.of()) : null,
                ratings.get(m.getId()))).toList();
    }

    public static Function<String, String> titleFromFirstMessage() {
        return content -> {
            String line = Texts.clean(content).replace('\n', ' ');
            return line.isBlank() ? DEFAULT_TITLE : Texts.truncate(line, 60);
        };
    }
}
