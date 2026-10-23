import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { errorMessage } from '../api/client';
import { chat } from '../api/endpoints';
import { streamChat, type SendBody, type ToolEvent } from '../api/sse';
import type { ConversationDetail, Message, Source } from '../api/types';

export interface ToolProgress {
  tool: string;
  label: string;
  state: 'running' | 'ok' | 'error';
}

export interface StreamState {
  conversationId: string | null;
  active: boolean;
  pendingUser: Message | null;
  replacesId: string | null;
  text: string;
  status: string | null;
  tools: ToolProgress[];
  sources: Source[];
  error: string | null;
}

const IDLE: StreamState = {
  conversationId: null, active: false, pendingUser: null, replacesId: null, text: '', status: null, tools: [],
  sources: [], error: null,
};

/** Runs one streamed chat turn at a time and merges the stored result into the conversation cache. */
export function useChatStream() {
  const qc = useQueryClient();
  const [state, setState] = useState<StreamState>(IDLE);
  const abort = useRef<AbortController | null>(null);

  useEffect(() => () => abort.current?.abort(), []);

  const start = useCallback(async (conversationId: string, body: SendBody) => {
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    setState({ ...IDLE, conversationId, active: true, status: 'Sending' });
    let meta: { userMessage: Message; replacesMessageId?: string; title: string } | null = null;

    const merge = (reply: Message) => {
      qc.setQueryData<ConversationDetail>(['conversation', conversationId], (old) => {
        if (!old) return old;
        const drop = new Set([reply.id, meta?.userMessage.id, meta?.replacesMessageId].filter(Boolean));
        const kept = old.messages.filter((m) => !drop.has(m.id));
        return { ...old, title: meta?.title ?? old.title, messages: meta ? [...kept, meta.userMessage, reply] : [...kept, reply] };
      });
    };

    try {
      await streamChat(conversationId, body, {
        onMeta: (m) => {
          meta = m;
          setState((s) => ({ ...s, pendingUser: m.userMessage, replacesId: m.replacesMessageId ?? null }));
          qc.invalidateQueries({ queryKey: ['conversations'] });
        },
        onStatus: (_stage, label) => setState((s) => ({ ...s, status: label })),
        onTool: (t: ToolEvent) => setState((s) => ({ ...s, tools: updateTools(s.tools, t) })),
        onContent: (delta) => setState((s) => ({ ...s, text: s.text + delta, status: null })),
        onReset: () => setState((s) => ({ ...s, text: '' })),
        onSources: (sources) => setState((s) => ({ ...s, sources })),
        onDone: (reply) => {
          merge(reply);
          setState(IDLE);
        },
        onError: (_code, message) => setState((s) => ({ ...s, active: false, error: message })),
      }, controller.signal);
      // The stream ended without a done event (e.g. a proxy closed it): reload what the server stored.
      setState((s) => (s.active ? { ...s, active: false } : s));
    } catch (e) {
      if ((e as Error).name === 'AbortError') return;
      setState((s) => ({ ...s, active: false, error: errorMessage(e) }));
    } finally {
      qc.invalidateQueries({ queryKey: ['conversation', conversationId] });
      qc.invalidateQueries({ queryKey: ['conversations'] });
    }
  }, [qc]);

  /** Asks the server to stop; it still sends the partial answer as a cancelled message. */
  const stop = useCallback(async () => {
    if (!state.conversationId) return;
    setState((s) => ({ ...s, status: 'Stopping' }));
    try {
      await chat.cancel(state.conversationId);
    } catch {
      abort.current?.abort();
      setState(IDLE);
    }
  }, [state.conversationId]);

  const clearError = useCallback(() => setState((s) => ({ ...s, error: null, pendingUser: null })), []);

  return { state, start, stop, clearError };
}

function updateTools(tools: ToolProgress[], t: ToolEvent): ToolProgress[] {
  if (t.state === 'started') {
    return [...tools, { tool: t.tool, label: t.label ?? t.tool, state: 'running' }];
  }
  const index = tools.findLastIndex((x) => x.tool === t.tool && x.state === 'running');
  if (index === -1) return tools;
  const next = [...tools];
  next[index] = { ...next[index], state: t.status === 'OK' ? 'ok' : 'error' };
  return next;
}
