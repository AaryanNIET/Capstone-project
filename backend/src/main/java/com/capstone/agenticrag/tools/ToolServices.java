package com.capstone.agenticrag.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import com.capstone.agenticrag.chat.ConversationRepository;
import com.capstone.agenticrag.chat.ConversationSummaryRepository;
import com.capstone.agenticrag.chat.MessageRepository;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.document.DocumentRepository;
import com.capstone.agenticrag.memory.MemoryService;
import com.capstone.agenticrag.rag.RetrievalService;
import com.capstone.agenticrag.search.LiveSearchService;
import com.capstone.agenticrag.search.WebPageFetcher;

/** The narrow set of application services tools may use. No repository grants cross-user or raw SQL access. */
@Component
public record ToolServices(RetrievalService retrieval, DocumentRepository documents, LiveSearchService search,
                           WebPageFetcher fetcher, MemoryService memories, ConversationRepository conversations,
                           MessageRepository messages, ConversationSummaryRepository summaries, ObjectMapper json,
                           AppProperties props) {}
