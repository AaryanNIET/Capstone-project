package com.capstone.agenticrag.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

import com.capstone.agenticrag.chat.Message;
import com.capstone.agenticrag.common.Texts;
import com.capstone.agenticrag.config.AiModels;
import com.capstone.agenticrag.config.AppProperties;
import com.capstone.agenticrag.memory.Memory;
import com.capstone.agenticrag.search.LiveSearchService;
import com.capstone.agenticrag.tools.AgentToolset;
import com.capstone.agenticrag.tools.ToolExecutor;
import com.capstone.agenticrag.tools.ToolNames;
import com.capstone.agenticrag.tools.ToolServices;

/**
 * Explicit, bounded agent loop. Each iteration streams one model response; if the model requests tools, the
 * {@link ToolExecutor} validates and runs them and their structured results are fed back. The loop ends when the model
 * answers without tool calls, or is forced to answer on the last iteration (tools withheld), or on cancellation,
 * deadline or error. Spring AI's internal tool execution is disabled so the server keeps control.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)\\]");
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(60);
    private static final int HISTORY_CHAR_BUDGET = 24_000;

    private final AiModels models;
    private final ToolServices toolServices;
    private final ToolExecutor toolExecutor;
    private final SystemPromptBuilder prompts;
    private final LiveSearchService search;
    private final AppProperties.Agent config;

    public AgentOrchestrator(AiModels models, ToolServices toolServices, ToolExecutor toolExecutor,
                             SystemPromptBuilder prompts, LiveSearchService search, AppProperties props) {
        this.models = models;
        this.toolServices = toolServices;
        this.toolExecutor = toolExecutor;
        this.prompts = prompts;
        this.search = search;
        this.config = props.agent();
    }

    private record StreamOutcome(String text, List<AssistantMessage.ToolCall> toolCalls, Integer promptTokens,
                                 Integer completionTokens, boolean cancelled) {}

    private static final class AgentFailure extends RuntimeException {
        final AgentResult.Status status;
        final String code;

        AgentFailure(AgentResult.Status status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    public AgentResult run(AgentRequest request, AgentListener listener) {
        AgentRunContext ctx = request.context();
        ChatModel model = models.chat().orElse(null);
        if (model == null) {
            return result(ctx, "", AgentResult.Status.FAILED, "AI_NOT_CONFIGURED",
                    "No AI model is configured on the server. Set AI_PROVIDER and AI_API_KEY.", 0, null, null);
        }

        List<SystemPromptBuilder.MemoryLine> memoryLines = new ArrayList<>();
        for (Memory m : request.memories()) {
            RunSource src = AgentToolset.registerMemory(ctx, m);
            memoryLines.add(new SystemPromptBuilder.MemoryLine(src.citation(), m.getCategory().name().toLowerCase(), m.getContent()));
        }
        if (!memoryLines.isEmpty()) {
            ctx.markUsedMemory();
        }

        Set<String> allowedNames = ToolNames.allowed(ctx.mode(), search.isAvailable(), ctx.memoryEnabled());
        Map<String, ToolCallback> tools = new LinkedHashMap<>();
        for (ToolCallback cb : MethodToolCallbackProvider.builder().toolObjects(new AgentToolset(ctx, toolServices))
                .build().getToolCallbacks()) {
            if (allowedNames.contains(cb.getToolDefinition().name())) {
                tools.put(cb.getToolDefinition().name(), cb);
            }
        }

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(prompts.build(ctx.mode(), request.userName(), search.isAvailable(),
                ctx.memoryEnabled(), request.readyDocuments(), memoryLines, request.savedMemoryNotes(), request.summary())));
        messages.addAll(history(request.history()));
        messages.add(new UserMessage(ctx.userMessage()));

        StringBuilder answer = new StringBuilder();
        Integer promptTokens = null;
        Integer completionTokens = null;
        int iteration = 0;
        try {
            while (true) {
                iteration++;
                ctx.setIteration(iteration);
                if (ctx.isCancelled()) {
                    return result(ctx, answer.toString(), AgentResult.Status.CANCELLED, null, null, iteration, promptTokens, completionTokens);
                }
                if (ctx.isExpired()) {
                    throw new AgentFailure(AgentResult.Status.TIMED_OUT, "TIMEOUT", "The request took too long and was stopped.");
                }
                boolean lastIteration = iteration >= config.maxIterations();
                boolean offerTools = !tools.isEmpty() && !lastIteration && ctx.executedToolCalls() < config.maxToolCalls();
                if (!offerTools && iteration > 1) {
                    messages.add(new SystemMessage("No more tool calls are available for this request. Write the final "
                            + "answer now using only the information gathered above, with citations."));
                }
                listener.onStatus(iteration == 1 ? "thinking" : "composing", iteration == 1 ? "Thinking" : "Composing the answer");

                StreamOutcome out = streamOnce(model, new Prompt(messages, options(model, tools.values(), offerTools)), ctx, listener, answer);
                promptTokens = add(promptTokens, out.promptTokens());
                completionTokens = add(completionTokens, out.completionTokens());
                if (out.cancelled()) {
                    return result(ctx, answer.toString(), AgentResult.Status.CANCELLED, null, null, iteration, promptTokens, completionTokens);
                }
                if (out.toolCalls().isEmpty() || !offerTools) {
                    if (answer.toString().isBlank()) {
                        throw new AgentFailure(AgentResult.Status.FAILED, "EMPTY_RESPONSE",
                                "The AI model returned an empty response. Please try again.");
                    }
                    return result(ctx, answer.toString(), AgentResult.Status.COMPLETED, null, null, iteration, promptTokens, completionTokens);
                }

                // The model asked for tools: any preamble text is not part of the final answer.
                if (!answer.isEmpty()) {
                    answer.setLength(0);
                    listener.onContentReset();
                }
                messages.add(AssistantMessage.builder().content(out.text()).toolCalls(out.toolCalls()).build());
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall call : out.toolCalls()) {
                    if (ctx.isCancelled()) {
                        responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(),
                                "{\"status\":\"error\",\"message\":\"cancelled\"}"));
                        continue;
                    }
                    responses.add(toolExecutor.execute(call, tools, ctx, listener));
                }
                messages.add(ToolResponseMessage.builder().responses(responses).build());
            }
        } catch (AgentFailure f) {
            return result(ctx, answer.toString(), f.status, f.code, f.getMessage(), iteration, promptTokens, completionTokens);
        }
    }

    private StreamOutcome streamOnce(ChatModel model, Prompt prompt, AgentRunContext ctx, AgentListener listener,
                                     StringBuilder answer) {
        StringBuilder text = new StringBuilder();
        Map<String, AssistantMessage.ToolCall> calls = new LinkedHashMap<>();
        Integer promptTokens = null;
        Integer completionTokens = null;
        Duration remaining = ctx.remaining();
        try (Stream<ChatResponse> stream = model.stream(prompt).timeout(IDLE_TIMEOUT).take(remaining).toStream(1)) {
            Iterator<ChatResponse> it = stream.iterator();
            while (it.hasNext()) {
                if (ctx.isCancelled()) {
                    return new StreamOutcome(text.toString(), List.of(), promptTokens, completionTokens, true);
                }
                ChatResponse response = it.next();
                if (response == null) {
                    continue;
                }
                if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                    Usage u = response.getMetadata().getUsage();
                    promptTokens = max(promptTokens, u.getPromptTokens());
                    completionTokens = max(completionTokens, u.getCompletionTokens());
                }
                if (response.getResults() == null) {
                    continue;
                }
                for (var generation : response.getResults()) {
                    AssistantMessage output = generation.getOutput();
                    if (output == null) {
                        continue;
                    }
                    String delta = output.getText();
                    if (delta != null && !delta.isEmpty()) {
                        text.append(delta);
                        answer.append(delta);
                        listener.onContent(delta);
                    }
                    for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
                        String key = tc.id() == null || tc.id().isBlank() ? "idx-" + calls.size() : tc.id();
                        AssistantMessage.ToolCall previous = calls.get(key);
                        if (previous == null || (tc.arguments() != null && tc.arguments().length() > lengthOf(previous.arguments()))) {
                            String id = tc.id() == null || tc.id().isBlank() ? "call_" + ctx.runId().toString().substring(0, 8) + "_" + calls.size() : tc.id();
                            calls.put(key, new AssistantMessage.ToolCall(id, tc.type() == null ? "function" : tc.type(), tc.name(), tc.arguments()));
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            throw translate(e);
        }
        if (ctx.isExpired() && calls.isEmpty()) {
            throw new AgentFailure(AgentResult.Status.TIMED_OUT, "TIMEOUT", "The request took too long and was stopped.");
        }
        return new StreamOutcome(text.toString(), List.copyOf(calls.values()), promptTokens, completionTokens, false);
    }

    private AgentFailure translate(RuntimeException e) {
        Throwable root = e;
        while (root.getCause() != null && root != root.getCause()) {
            if (root instanceof TimeoutException || root instanceof NonTransientAiException) {
                break;
            }
            root = root.getCause();
        }
        if (root instanceof TimeoutException || e instanceof IllegalStateException && String.valueOf(e.getMessage()).contains("Timeout")) {
            return new AgentFailure(AgentResult.Status.TIMED_OUT, "AI_TIMEOUT", "The AI provider did not respond in time. Please try again.");
        }
        String message = String.valueOf(root.getMessage());
        log.warn("AI provider call failed: {}: {}", root.getClass().getSimpleName(), Texts.truncate(message, 300));
        if (message.startsWith("401") || message.contains("invalid_api_key") || message.contains("Incorrect API key")) {
            return new AgentFailure(AgentResult.Status.FAILED, "AI_AUTH_FAILED", "The AI provider rejected the API key. Check AI_API_KEY.");
        }
        if (message.startsWith("404") || message.contains("model_not_found")) {
            return new AgentFailure(AgentResult.Status.FAILED, "AI_MODEL_NOT_FOUND", "The configured AI model was not found. Check AI_MODEL.");
        }
        if (message.startsWith("429") || message.contains("rate_limit") || message.contains("insufficient_quota")) {
            return new AgentFailure(AgentResult.Status.FAILED, "AI_RATE_LIMITED", "The AI provider is rate-limiting requests or the quota is exhausted. Please try again later.");
        }
        return new AgentFailure(AgentResult.Status.FAILED, "AI_ERROR", "The AI provider returned an error. Please try again.");
    }

    /** Chat options that expose the allowed tools while disabling Spring AI's internal tool execution. */
    private static ChatOptions options(ChatModel model, Collection<ToolCallback> tools, boolean offerTools) {
        List<ToolCallback> list = List.copyOf(tools);
        if (model instanceof OpenAiChatModel) {
            OpenAiChatOptions.Builder b = OpenAiChatOptions.builder().internalToolExecutionEnabled(false);
            if (!list.isEmpty()) {
                // History may contain tool calls, so keep the definitions but forbid new calls on the final turn.
                b.toolCallbacks(list).toolChoice(offerTools ? "auto" : "none");
            }
            return b.build();
        }
        return ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)
                .toolCallbacks(offerTools ? list : List.of()).build();
    }

    /** Recent history as model messages, newest kept within a character budget; old citation markers removed. */
    private static List<org.springframework.ai.chat.messages.Message> history(List<Message> history) {
        List<org.springframework.ai.chat.messages.Message> out = new ArrayList<>();
        int budget = HISTORY_CHAR_BUDGET;
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.getStatus() == Message.Status.FAILED || m.getContent().isBlank()) {
                continue;
            }
            String content = Texts.truncate(m.getContent(), 4000);
            budget -= content.length();
            if (budget < 0) {
                break;
            }
            out.addFirst(m.getRole() == Message.Role.USER ? new UserMessage(content)
                    : new AssistantMessage(CITATION.matcher(content).replaceAll("")));
        }
        return out;
    }

    private AgentResult result(AgentRunContext ctx, String rawContent, AgentResult.Status status, String code,
                               String message, int iterations, Integer promptTokens, Integer completionTokens) {
        List<RunSource> registered = ctx.sources();
        Set<Integer> valid = registered.stream().map(RunSource::citation).collect(Collectors.toSet());
        Set<Integer> cited = new TreeSet<>();
        String content = sanitizeCitations(rawContent == null ? "" : rawContent.strip(), valid, cited);
        List<RunSource> sources = registered.stream().map(s -> s.withCited(cited.contains(s.citation()))).toList();
        return new AgentResult(content, status, code, message, iterations, ctx.toolCalls(), sources,
                ctx.usedDocuments(), ctx.usedWeb(), ctx.usedMemory(), promptTokens, completionTokens);
    }

    /**
     * Keeps only citation markers that refer to sources registered in this run; fabricated numbers are removed.
     * Fenced code blocks and inline code are left untouched (so {@code arr[1]} in code survives).
     */
    static String sanitizeCitations(String content, Set<Integer> valid, Set<Integer> cited) {
        Matcher code = CODE.matcher(content);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (code.find()) {
            out.append(sanitizeProse(content.substring(last, code.start()), valid, cited)).append(code.group());
            last = code.end();
        }
        return out.append(sanitizeProse(content.substring(last), valid, cited)).toString();
    }

    private static final Pattern CODE = Pattern.compile("(?s)```.*?(```|$)|`[^`\\n]+`");

    private static String sanitizeProse(String content, Set<Integer> valid, Set<Integer> cited) {
        Matcher m = CITATION.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder replacement = new StringBuilder();
            for (String part : m.group(1).split(",")) {
                int n = Integer.parseInt(part.strip());
                if (valid.contains(n)) {
                    cited.add(n);
                    replacement.append('[').append(n).append(']');
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static int lengthOf(String s) {
        return s == null ? 0 : s.length();
    }

    private static Integer max(Integer a, Integer b) {
        if (b == null || b == 0) {
            return a;
        }
        return a == null ? b : Math.max(a, b);
    }

    private static Integer add(Integer a, Integer b) {
        if (b == null) {
            return a;
        }
        return a == null ? b : a + b;
    }
}
