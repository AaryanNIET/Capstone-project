package com.capstone.agenticrag.tools;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.capstone.agenticrag.chat.AssistantMode;

/** Tool names and which assistant mode may use which tool. */
public final class ToolNames {

    public static final String SEARCH_DOCUMENTS = "searchDocuments";
    public static final String LIST_DOCUMENTS = "listDocuments";
    public static final String SUMMARIZE_DOCUMENT = "summarizeDocument";
    public static final String SEARCH_LIVE_WEB = "searchLiveWeb";
    public static final String READ_WEB_PAGE = "readWebPage";
    public static final String RETRIEVE_MEMORIES = "retrieveRelevantMemories";
    public static final String SAVE_MEMORY = "saveMemory";
    public static final String CONVERSATION_CONTEXT = "retrieveConversationContext";
    public static final String SEARCH_PAST_CONVERSATIONS = "searchPastConversations";
    public static final String CURRENT_DATE_TIME = "getCurrentDateTime";

    private static final Set<String> DOCUMENT_TOOLS = Set.of(SEARCH_DOCUMENTS, LIST_DOCUMENTS, SUMMARIZE_DOCUMENT);
    private static final Set<String> WEB_TOOLS = Set.of(SEARCH_LIVE_WEB, READ_WEB_PAGE);
    private static final Set<String> MEMORY_TOOLS = Set.of(RETRIEVE_MEMORIES, SAVE_MEMORY);
    private static final Set<String> CONTEXT_TOOLS = Set.of(CONVERSATION_CONTEXT, SEARCH_PAST_CONVERSATIONS, CURRENT_DATE_TIME);

    private static final Map<AssistantMode, Set<String>> BY_MODE = new EnumMap<>(AssistantMode.class);

    static {
        BY_MODE.put(AssistantMode.AUTO, union(DOCUMENT_TOOLS, WEB_TOOLS, MEMORY_TOOLS, CONTEXT_TOOLS));
        BY_MODE.put(AssistantMode.DOCUMENTS, union(DOCUMENT_TOOLS, MEMORY_TOOLS, CONTEXT_TOOLS));
        BY_MODE.put(AssistantMode.WEB, union(WEB_TOOLS, MEMORY_TOOLS, CONTEXT_TOOLS));
        BY_MODE.put(AssistantMode.CHAT, union(MEMORY_TOOLS, CONTEXT_TOOLS));
    }

    private ToolNames() {}

    /** Tools allowed for a mode, given which optional capabilities are currently available. */
    public static Set<String> allowed(AssistantMode mode, boolean webAvailable, boolean memoryEnabled) {
        Set<String> names = new LinkedHashSet<>(BY_MODE.get(mode));
        if (!webAvailable) {
            // Keep searchLiveWeb so the model gets an explicit "unavailable" result instead of guessing.
            names.remove(READ_WEB_PAGE);
        }
        if (!memoryEnabled) {
            names.removeAll(MEMORY_TOOLS);
        }
        return names;
    }

    public static String label(String tool) {
        return switch (tool) {
            case SEARCH_DOCUMENTS -> "Searching your documents";
            case LIST_DOCUMENTS -> "Listing your documents";
            case SUMMARIZE_DOCUMENT -> "Reviewing a document";
            case SEARCH_LIVE_WEB -> "Searching the web";
            case READ_WEB_PAGE -> "Reading a web page";
            case RETRIEVE_MEMORIES -> "Checking memory";
            case SAVE_MEMORY -> "Saving to memory";
            case CONVERSATION_CONTEXT -> "Reviewing conversation history";
            case SEARCH_PAST_CONVERSATIONS -> "Searching past conversations";
            case CURRENT_DATE_TIME -> "Checking the date and time";
            default -> "Running a tool";
        };
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> out = new LinkedHashSet<>();
        for (Set<String> s : sets) {
            out.addAll(s);
        }
        return Set.copyOf(out);
    }
}
