package com.capstone.agenticrag.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.agenticrag.common.PageResponse;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.exception.ApiException;
import com.capstone.agenticrag.memory.MemoryDtos.MemoryResponse;
import com.capstone.agenticrag.memory.MemoryDtos.MemorySettings;
import com.capstone.agenticrag.memory.MemoryVectorStore.Match;
import com.capstone.agenticrag.rag.EmbeddingService;
import com.capstone.agenticrag.user.User;
import com.capstone.agenticrag.user.UserRepository;

/**
 * Long-term memory. Writes go through {@link MemoryPolicy}; duplicates are merged and contradictions of single-valued
 * attributes are replaced rather than accumulated. When memory is disabled nothing is read into prompts and nothing
 * new is written; existing memories stay inspectable, editable and deletable.
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    public enum Outcome { CREATED, UPDATED, UNCHANGED, REJECTED }

    public record SaveResult(Outcome outcome, Memory memory, String reason) {
        static SaveResult rejected(String reason) {
            return new SaveResult(Outcome.REJECTED, null, reason);
        }
    }

    public record ScoredMemory(Memory memory, double score) {}

    private final MemoryRepository memories;
    private final MemoryVectorStore vectors;
    private final MemoryPolicy policy;
    private final EmbeddingService embeddings;
    private final UserRepository users;
    private final AppProperties.Memory config;

    public MemoryService(MemoryRepository memories, MemoryVectorStore vectors, MemoryPolicy policy,
                         EmbeddingService embeddings, UserRepository users, AppProperties props) {
        this.memories = memories;
        this.vectors = vectors;
        this.policy = policy;
        this.embeddings = embeddings;
        this.users = users;
        this.config = props.memory();
    }

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public MemorySettings settings(UUID userId) {
        User u = user(userId);
        return new MemorySettings(u.isMemoryEnabled(), u.isDeleteMemoriesWithConversation());
    }

    @Transactional
    public MemorySettings updateSettings(UUID userId, MemoryDtos.UpdateMemorySettingsRequest req) {
        User u = user(userId);
        if (req.enabled() != null) {
            u.setMemoryEnabled(req.enabled());
        }
        if (req.deleteWithConversation() != null) {
            u.setDeleteMemoriesWithConversation(req.deleteWithConversation());
        }
        return new MemorySettings(u.isMemoryEnabled(), u.isDeleteMemoriesWithConversation());
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId) {
        return user(userId).isMemoryEnabled();
    }

    // ------------------------------------------------------------------ CRUD for the settings screen

    @Transactional(readOnly = true)
    public PageResponse<MemoryResponse> list(UUID userId, Memory.Category category, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200));
        return PageResponse.from(category == null
                ? memories.findByUserIdOrderByUpdatedAtDesc(userId, pageable)
                : memories.findByUserIdAndCategoryOrderByUpdatedAtDesc(userId, category, pageable), MemoryResponse::from);
    }

    @Transactional
    public MemoryResponse createManual(UUID userId, String content, Memory.Category category) {
        SaveResult r = remember(userId, content, category, Memory.Origin.MANUAL, true, 1.0f, null, null, null);
        if (r.outcome() == Outcome.REJECTED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEMORY_REJECTED", r.reason());
        }
        return MemoryResponse.from(r.memory());
    }

    @Transactional
    public MemoryResponse update(UUID userId, UUID memoryId, MemoryDtos.UpdateMemoryRequest req) {
        Memory m = memories.findByIdAndUserId(memoryId, userId).orElseThrow(() -> ApiException.notFound("Memory"));
        String content = req.content() == null ? m.getContent() : req.content().strip();
        MemoryPolicy.Decision d = policy.evaluate(content, true);
        if (d != MemoryPolicy.Decision.ALLOW) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEMORY_REJECTED", MemoryPolicy.describe(d).orElse(""));
        }
        boolean contentChanged = !content.equals(m.getContent());
        m.edit(content, req.category() == null ? m.getCategory() : req.category());
        if (contentChanged) {
            embedQuietly(m);
        }
        return MemoryResponse.from(m);
    }

    @Transactional
    public void delete(UUID userId, UUID memoryId) {
        Memory m = memories.findByIdAndUserId(memoryId, userId).orElseThrow(() -> ApiException.notFound("Memory"));
        memories.delete(m);
    }

    @Transactional
    public int clearAll(UUID userId) {
        return memories.deleteAllByUserId(userId);
    }

    @Transactional
    public int deleteBySourceConversation(UUID userId, UUID conversationId) {
        return memories.deleteBySourceConversation(userId, conversationId);
    }

    // ------------------------------------------------------------------ write path used by the agent

    /**
     * Stores a memory if policy allows, merging with an equivalent existing memory where possible.
     *
     * @param exclusivePrefix if non-null, existing memories of the same category starting with this prefix describe the
     *                        same single-valued attribute and are replaced (contradiction resolution: newest wins).
     */
    @Transactional
    public SaveResult remember(UUID userId, String content, Memory.Category category, Memory.Origin origin,
                               boolean explicit, Float confidence, String exclusivePrefix, UUID conversationId,
                               UUID messageId) {
        if (!user(userId).isMemoryEnabled()) {
            return SaveResult.rejected("Memory is turned off in your settings.");
        }
        String text = content == null ? "" : Texts.clean(content);
        MemoryPolicy.Decision decision = policy.evaluate(text, explicit);
        if (decision != MemoryPolicy.Decision.ALLOW) {
            return SaveResult.rejected(MemoryPolicy.describe(decision).orElse("Rejected by memory policy."));
        }
        List<Memory> existing = memories.findByUserId(userId);

        String normalized = Texts.normalizeForComparison(text);
        for (Memory m : existing) {
            if (Texts.normalizeForComparison(m.getContent()).equals(normalized)) {
                m.revise(m.getContent(), m.getCategory(), conversationId, messageId, confidence);
                return new SaveResult(Outcome.UNCHANGED, m, null);
            }
        }
        if (exclusivePrefix != null) {
            String prefix = exclusivePrefix.toLowerCase(Locale.ROOT);
            for (Memory m : existing) {
                if (m.getCategory() == category && m.getContent().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    m.revise(text, category, conversationId, messageId, confidence);
                    embedQuietly(m);
                    return new SaveResult(Outcome.UPDATED, m, null);
                }
            }
        }

        float[] vector = null;
        if (embeddings.isAvailable()) {
            try {
                vector = embeddings.embed(text);
                List<Match> nearest = vectors.nearest(userId, vector, 1);
                if (!nearest.isEmpty() && nearest.getFirst().score() >= config.duplicateThreshold()) {
                    Memory m = memories.findByIdAndUserId(nearest.getFirst().id(), userId).orElse(null);
                    if (m != null) {
                        m.revise(text, category, conversationId, messageId, confidence);
                        vectors.setEmbedding(m.getId(), vector);
                        return new SaveResult(Outcome.UPDATED, m, null);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Memory embedding failed; storing without vector: {}", e.getClass().getSimpleName());
                vector = null;
            }
        }
        if (existing.size() >= config.maxPerUser()) {
            return SaveResult.rejected("The memory limit of " + config.maxPerUser() + " entries has been reached.");
        }
        Memory created = memories.saveAndFlush(new Memory(userId, text, category, origin, conversationId, messageId, confidence));
        if (vector != null) {
            vectors.setEmbedding(created.getId(), vector);
        }
        return new SaveResult(Outcome.CREATED, created, null);
    }

    // ------------------------------------------------------------------ read path used by the agent

    /**
     * Returns memories relevant to {@code query}: semantic matches above the similarity threshold, keyword matches as a
     * complement/fallback, plus standing INSTRUCTION memories which apply to every answer. Empty when disabled.
     */
    @Transactional
    public List<ScoredMemory> retrieveRelevant(UUID userId, String query, Integer topK) {
        if (!user(userId).isMemoryEnabled()) {
            return List.of();
        }
        int k = topK == null ? config.topK() : Math.clamp(topK, 1, 20);
        Map<UUID, Double> scores = new LinkedHashMap<>();
        if (embeddings.isAvailable() && query != null && !query.isBlank()) {
            try {
                for (Match m : vectors.nearest(userId, embeddings.embed(query), k)) {
                    if (m.score() >= config.similarityThreshold()) {
                        scores.put(m.id(), m.score());
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Semantic memory search failed, using keyword matching: {}", e.getClass().getSimpleName());
            }
        }
        List<Memory> active = memories.findActive(userId, Instant.now(), PageRequest.of(0, config.maxPerUser()));
        Set<String> queryWords = Texts.contentWords(query);
        if (scores.size() < k && !queryWords.isEmpty()) {
            active.stream()
                    .filter(m -> !scores.containsKey(m.getId()))
                    .map(m -> new ScoredMemory(m, overlap(queryWords, Texts.contentWords(m.getContent()))))
                    .filter(s -> s.score() >= 0.2)
                    .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                    .limit(k - scores.size())
                    .forEach(s -> scores.put(s.memory().getId(), s.score()));
        }
        active.stream().filter(m -> m.getCategory() == Memory.Category.INSTRUCTION).limit(5)
                .forEach(m -> scores.putIfAbsent(m.getId(), 1.0));

        Map<UUID, Memory> byId = active.stream().collect(Collectors.toMap(Memory::getId, Function.identity()));
        List<ScoredMemory> result = new ArrayList<>();
        scores.forEach((id, score) -> {
            Memory m = byId.get(id);
            if (m != null) {
                result.add(new ScoredMemory(m, score));
            }
        });
        if (!result.isEmpty()) {
            memories.markUsed(result.stream().map(s -> s.memory().getId()).toList(), Instant.now());
        }
        return result;
    }

    private static double overlap(Set<String> query, Set<String> memory) {
        if (memory.isEmpty()) {
            return 0;
        }
        long common = memory.stream().filter(query::contains).count();
        return common / (double) Math.min(query.size(), memory.size());
    }

    private void embedQuietly(Memory m) {
        if (!embeddings.isAvailable()) {
            vectors.setEmbedding(m.getId(), null);
            return;
        }
        try {
            vectors.setEmbedding(m.getId(), embeddings.embed(m.getContent()));
        } catch (RuntimeException e) {
            vectors.setEmbedding(m.getId(), null);
        }
    }

    private User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
