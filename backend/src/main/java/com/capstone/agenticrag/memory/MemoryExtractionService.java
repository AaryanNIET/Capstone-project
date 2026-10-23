package com.capstone.agenticrag.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.capstone.agenticrag.config.AppProperties;

/**
 * Runs before the agent on each new user message: stores the durable facts and explicit "remember ..." requests the
 * {@link MemoryCandidateExtractor} finds, and returns notes so the assistant can confirm what it saved. Only the user's
 * own words are considered, never assistant output or tool results.
 */
@Service
public class MemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);

    private final MemoryCandidateExtractor extractor;
    private final MemoryService memories;
    private final AppProperties.Memory config;

    public MemoryExtractionService(MemoryCandidateExtractor extractor, MemoryService memories, AppProperties props) {
        this.extractor = extractor;
        this.memories = memories;
        this.config = props.memory();
    }

    /** Returns the content of each memory that was created or updated. Failures never block the chat turn. */
    public List<String> extractAndSave(UUID userId, UUID conversationId, UUID messageId, String userMessage) {
        List<String> notes = new ArrayList<>();
        for (MemoryCandidateExtractor.Candidate c : extractor.extract(userMessage)) {
            if (!c.explicit() && !config.autoExtract()) {
                continue;
            }
            try {
                MemoryService.SaveResult r = memories.remember(userId, c.content(), c.category(),
                        c.explicit() ? Memory.Origin.EXPLICIT : Memory.Origin.EXTRACTED, c.explicit(), c.confidence(),
                        c.exclusivePrefix(), conversationId, messageId);
                switch (r.outcome()) {
                    case CREATED, UPDATED -> notes.add(r.memory().getContent());
                    case REJECTED -> {
                        if (c.explicit()) {
                            notes.add("Not saved: " + r.reason());
                        }
                    }
                    case UNCHANGED -> { }
                }
            } catch (RuntimeException e) {
                log.warn("Memory extraction failed for a candidate: {}", e.getClass().getSimpleName());
            }
        }
        return notes;
    }
}
