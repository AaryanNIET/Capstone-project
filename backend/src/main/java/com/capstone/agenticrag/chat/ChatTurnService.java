package com.capstone.agenticrag.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.agenticrag.agent.AgentExecution;
import com.capstone.agenticrag.agent.AgentExecutionRepository;
import com.capstone.agenticrag.agent.AgentResult;
import com.capstone.agenticrag.agent.AgentRunContext;
import com.capstone.agenticrag.agent.RunSource;
import com.capstone.agenticrag.chat.ChatDtos.MessageDto;
import com.capstone.agenticrag.chat.ChatDtos.SendMessageRequest;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.document.DocumentRepository;
import com.capstone.agenticrag.document.UserDocument;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.source.MessageSource;
import com.capstone.agenticrag.source.MessageSourceRepository;
import com.capstone.agenticrag.user.User;
import com.capstone.agenticrag.user.UserService;

/**
 * The two short transactions around an agent run. {@link #prepare} persists the user message and loads the context;
 * {@link #complete} persists the assistant reply, its sources and the execution record. No transaction is held open
 * while the model is generating.
 */
@Service
public class ChatTurnService {

    /**
     * Everything loaded for one turn.
     *
     * @param replacedAssistantId the assistant reply that a regenerate replaces once the new reply is stored, or null
     */
    public record PreparedTurn(UUID userId, UUID conversationId, AssistantMode mode, Message userMessage,
                               boolean regenerate, UUID replacedAssistantId, List<Message> history, String summary,
                               String userName, boolean memoryEnabled, long readyDocuments, String title) {}

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationSummaryRepository summaries;
    private final MessageSourceRepository sources;
    private final AgentExecutionRepository executions;
    private final DocumentRepository documents;
    private final UserService users;
    private final ConversationService conversationService;
    private final AppProperties.Agent config;

    public ChatTurnService(ConversationRepository conversations, MessageRepository messages,
                           ConversationSummaryRepository summaries, MessageSourceRepository sources,
                           AgentExecutionRepository executions, DocumentRepository documents, UserService users,
                           ConversationService conversationService, AppProperties props) {
        this.conversations = conversations;
        this.messages = messages;
        this.summaries = summaries;
        this.sources = sources;
        this.executions = executions;
        this.documents = documents;
        this.users = users;
        this.conversationService = conversationService;
        this.config = props.agent();
    }

    @Transactional
    public PreparedTurn prepare(UUID userId, UUID conversationId, SendMessageRequest req) {
        Conversation conversation = conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
        User user = users.get(userId);
        if (req.mode() != null && req.mode() != conversation.getMode()) {
            conversation.setMode(req.mode());
        }
        AssistantMode mode = conversation.getMode();

        Message userMessage;
        UUID replaced = null;
        if (req.isRegenerate()) {
            if (req.content() != null && !req.content().isBlank()) {
                throw ApiException.badRequest("Send either a new message or regenerate=true, not both");
            }
            Message latest = messages.findFirstByConversationIdOrderByCreatedAtDesc(conversationId)
                    .orElseThrow(() -> ApiException.badRequest("There is no message to regenerate"));
            if (latest.getRole() == Message.Role.ASSISTANT) {
                replaced = latest.getId();
                userMessage = messages.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 2))
                        .stream().filter(m -> m.getRole() == Message.Role.USER).findFirst()
                        .orElseThrow(() -> ApiException.badRequest("There is no message to regenerate"));
            } else {
                userMessage = latest;
            }
        } else {
            String content = req.content() == null ? "" : Texts.clean(req.content());
            if (content.isBlank()) {
                throw ApiException.badRequest("Message must not be empty");
            }
            userMessage = messages.saveAndFlush(new Message(conversationId, userId, Message.Role.USER, content, mode));
            if (ConversationService.DEFAULT_TITLE.equals(conversation.getTitle())
                    && messages.countByConversationId(conversationId) == 1) {
                conversation.setTitle(ConversationService.titleFromFirstMessage().apply(content));
            }
        }
        conversation.touch();

        String summary = summaries.findByConversationIdAndUserId(conversationId, userId)
                .map(ConversationSummary::getSummary).orElse(null);
        return new PreparedTurn(userId, conversationId, mode, userMessage, req.isRegenerate(), replaced,
                history(conversationId, userMessage, replaced), summary, user.getName(), user.isMemoryEnabled(),
                documents.countByUserIdAndStatus(userId, UserDocument.Status.READY), conversation.getTitle());
    }

    /** The most recent messages before the turn's user message, in chronological order. */
    private List<Message> history(UUID conversationId, Message userMessage, UUID replaced) {
        List<Message> recent = messages.findByConversationIdOrderByCreatedAtDesc(conversationId,
                PageRequest.of(0, config.recentMessages() + 2));
        List<Message> out = new ArrayList<>();
        for (Message m : recent) {
            if (m.getId().equals(userMessage.getId()) || m.getId().equals(replaced)
                    || m.getCreatedAt().isAfter(userMessage.getCreatedAt())) {
                continue;
            }
            if (out.size() == config.recentMessages()) {
                break;
            }
            out.addFirst(m);
        }
        return out;
    }

    @Transactional
    public MessageDto complete(PreparedTurn turn, AgentRunContext ctx, AgentResult result, String modelName,
                               Instant startedAt) {
        Message reply = new Message(turn.conversationId(), turn.userId(), Message.Role.ASSISTANT,
                result.content() == null ? "" : result.content(), turn.mode());
        reply.setStatus(switch (result.status()) {
            case COMPLETED -> Message.Status.COMPLETED;
            case CANCELLED -> Message.Status.CANCELLED;
            case FAILED, TIMED_OUT -> Message.Status.FAILED;
        });
        if (result.errorMessage() != null) {
            reply.setErrorMessage(Texts.truncate(result.errorMessage(), 500));
        }
        reply.setUsedDocuments(result.usedDocuments());
        reply.setUsedWeb(result.usedWeb());
        reply.setUsedMemory(result.usedMemory());
        reply.setPromptTokens(result.promptTokens());
        reply.setCompletionTokens(result.completionTokens());
        reply = messages.saveAndFlush(reply);

        List<MessageSource> rows = new ArrayList<>();
        for (RunSource s : result.sources()) {
            rows.add(new MessageSource(reply.getId(), s.citation(), s.type(),
                    Texts.truncate(s.title() == null || s.title().isBlank() ? "Untitled" : s.title(), 500),
                    s.url() == null || s.url().length() > 2048 ? null : s.url(), s.documentId(), s.chunkIndex(),
                    s.pageNumber(), Texts.truncate(s.snippet(), 2000), s.score(), s.cited(),
                    s.retrievedAt() == null ? Instant.now() : s.retrievedAt()));
        }
        sources.saveAll(rows);
        executions.save(new AgentExecution(ctx.runId(), turn.userId(), turn.conversationId(), reply.getId(),
                turn.mode(), modelName, result, startedAt));

        if (turn.replacedAssistantId() != null) {
            messages.findById(turn.replacedAssistantId())
                    .filter(m -> m.getConversationId().equals(turn.conversationId()))
                    .ifPresent(messages::delete);
        }
        conversations.findById(turn.conversationId()).ifPresent(Conversation::touch);
        return conversationService.toDtos(turn.userId(), List.of(reply)).getFirst();
    }

    @Transactional(readOnly = true)
    public MessageDto toDto(UUID userId, Message message) {
        return conversationService.toDtos(userId, List.of(message)).getFirst();
    }
}
