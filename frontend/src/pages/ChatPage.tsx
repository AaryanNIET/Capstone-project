import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { CheckCircle2, FileText, Globe, Loader2, MessageSquare, PanelLeft, Sparkles, XCircle } from 'lucide-react';

import { ApiError, errorMessage } from '../api/client';
import { conversations, messages as messageApi, system } from '../api/endpoints';
import type { ConversationDetail, Message, Mode, Rating } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Composer } from '../components/Composer';
import { ConversationSidebar } from '../components/ConversationSidebar';
import { Markdown } from '../components/Markdown';
import { MessageBubble } from '../components/MessageBubble';
import { FullPageSpinner } from '../components/Spinner';
import { Alert } from '../components/ui';
import { useChatStream, type StreamState } from '../hooks/useChatStream';
import { MODES } from '../lib/format';

export function ChatPage() {
  const { conversationId } = useParams();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const stream = useChatStream();

  return (
    <div className="flex h-full">
      <div className={clsx('fixed inset-y-14 left-0 z-30 w-72 md:static md:inset-auto md:block md:h-full',
        sidebarOpen ? 'block' : 'hidden')}>
        <ConversationSidebar activeId={conversationId} onNavigate={() => setSidebarOpen(false)} />
      </div>
      {sidebarOpen && <div className="fixed inset-0 top-14 z-20 bg-black/30 md:hidden" onClick={() => setSidebarOpen(false)} />}
      <section className="flex min-w-0 flex-1 flex-col">
        <button className="btn-ghost m-2 self-start md:hidden" onClick={() => setSidebarOpen(true)}>
          <PanelLeft className="h-4 w-4" /> Conversations
        </button>
        {conversationId
          ? <Conversation key={conversationId} id={conversationId} stream={stream} />
          : <NewChat stream={stream} />}
      </section>
    </div>
  );
}

type Stream = ReturnType<typeof useChatStream>;

function useSystemStatus() {
  return useQuery({ queryKey: ['system-status'], queryFn: system.status, staleTime: 60_000 });
}

function NewChat({ stream }: { stream: Stream }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [mode, setMode] = useState<Mode>(user?.defaultMode ?? 'AUTO');
  const [error, setError] = useState<string | null>(null);
  const status = useSystemStatus();

  const send = async (text: string) => {
    setError(null);
    try {
      const c = await conversations.create(mode);
      qc.setQueryData<ConversationDetail>(['conversation', c.id], { ...c, messages: [] });
      navigate(`/chat/${c.id}`);
      void stream.start(c.id, { content: text });
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-1 flex-col items-center justify-center overflow-y-auto px-4 py-8 text-center">
        <div className="mb-4 rounded-2xl bg-indigo-100 p-3 text-indigo-600 dark:bg-indigo-950 dark:text-indigo-300">
          <Sparkles className="h-7 w-7" aria-hidden />
        </div>
        <h1 className="text-2xl font-semibold">How can I help{user ? `, ${user.name.split(' ')[0]}` : ''}?</h1>
        <p className="muted mt-2 max-w-md text-sm">
          Ask about your uploaded documents, current events on the web, or anything else. Answers cite their sources.
        </p>
        <div className="mt-6 w-full max-w-xl">
          <ModePicker mode={mode} onChange={setMode} />
        </div>
        <div className="mt-4 w-full max-w-xl space-y-2 text-left">
          <StatusWarnings mode={mode} status={status.data} />
          {error && <Alert>{error}</Alert>}
        </div>
      </div>
      <Composer onSend={send} onStop={stream.stop} busy={stream.state.active} />
    </div>
  );
}

function Conversation({ id, stream }: { id: string; stream: Stream }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const status = useSystemStatus();
  const detail = useQuery({ queryKey: ['conversation', id], queryFn: () => conversations.get(id) });
  const { state } = stream;
  const streamingHere = state.conversationId === id;

  const setMode = useMutation({
    mutationFn: (mode: Mode) => conversations.update(id, { mode }),
    onSuccess: (c) => {
      qc.setQueryData<ConversationDetail>(['conversation', id], (old) => old && { ...old, mode: c.mode });
      qc.invalidateQueries({ queryKey: ['conversations'] });
    },
  });

  const rate = useMutation({
    mutationFn: ({ messageId, rating }: { messageId: string; rating: Rating | null }) =>
      rating ? messageApi.rate(messageId, rating) : messageApi.unrate(messageId),
    onMutate: ({ messageId, rating }) => {
      qc.setQueryData<ConversationDetail>(['conversation', id], (old) => old && {
        ...old,
        messages: old.messages.map((m) => (m.id === messageId ? { ...m, feedback: rating ?? undefined } : m)),
      });
    },
  });

  const shown: Message[] = useMemo(() => {
    const list = detail.data?.messages ?? [];
    if (!streamingHere) return list;
    const visible = list.filter((m) => m.id !== state.replacesId);
    if (state.pendingUser && !visible.some((m) => m.id === state.pendingUser!.id)) visible.push(state.pendingUser);
    return visible;
  }, [detail.data, streamingHere, state.replacesId, state.pendingUser]);

  const scroller = useAutoScroll([shown.length, state.text, state.tools.length, state.status]);

  if (detail.isPending) return <FullPageSpinner />;
  if (detail.isError) {
    const notFound = detail.error instanceof ApiError && detail.error.status === 404;
    return (
      <div className="m-6 space-y-3">
        <Alert>{notFound ? 'This conversation does not exist or was deleted.' : errorMessage(detail.error)}</Alert>
        <button className="btn-secondary" onClick={() => navigate('/chat')}>Start a new chat</button>
      </div>
    );
  }

  const c = detail.data;
  const busy = streamingHere && state.active;
  const lastAssistant = [...shown].reverse().find((m) => m.role === 'ASSISTANT');
  const send = (text: string) => stream.start(id, { content: text });

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 px-4 py-2 dark:border-slate-800">
        <h1 className="min-w-0 flex-1 truncate font-medium" title={c.title}>{c.title}</h1>
        <ModePicker compact mode={c.mode} onChange={(m) => setMode.mutate(m)} disabled={busy} />
      </div>
      <div ref={scroller} className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-3xl space-y-6 px-4 py-6">
          <StatusWarnings mode={c.mode} status={status.data} />
          {shown.length === 0 && !busy && (
            <p className="muted py-10 text-center text-sm">Send a message to start the conversation.</p>
          )}
          {shown.map((m) => (
            <MessageBubble
              key={m.id}
              message={m}
              canRegenerate={!busy && m.id === lastAssistant?.id}
              onRegenerate={() => stream.start(id, { regenerate: true })}
              onRate={m.role === 'ASSISTANT' ? (rating) => rate.mutate({ messageId: m.id, rating }) : undefined}
            />
          ))}
          {streamingHere && (state.active || state.error) && <StreamingReply state={state} />}
          {streamingHere && state.error && (
            <Alert>
              {state.error}{' '}
              <button className="font-medium underline" onClick={stream.clearError}>Dismiss</button>
            </Alert>
          )}
        </div>
      </div>
      <Composer onSend={send} onStop={stream.stop} busy={busy} disabled={stream.state.active && !streamingHere}
                placeholder={`Message (${c.mode.toLowerCase()} mode)…`} />
    </div>
  );
}

function StreamingReply({ state }: { state: StreamState }) {
  if (!state.active) return null;
  return (
    <div aria-live="polite">
      {state.tools.length > 0 && (
        <ul className="mb-3 flex flex-wrap gap-2">
          {state.tools.map((t, i) => (
            <li key={i} className="badge bg-slate-100 py-1 text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {t.state === 'running' ? <Loader2 className="h-3 w-3 animate-spin" />
                : t.state === 'ok' ? <CheckCircle2 className="h-3 w-3 text-emerald-600" /> : <XCircle className="h-3 w-3 text-red-500" />}
              {t.label}
            </li>
          ))}
        </ul>
      )}
      {state.text ? (
        <div className="relative">
          <Markdown content={state.text} />
          <span className="ml-0.5 inline-block h-4 w-1.5 animate-pulse bg-indigo-500 align-middle" aria-hidden />
        </div>
      ) : (
        <div className="muted flex items-center gap-2 text-sm">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          {state.status ?? 'Thinking'}…
        </div>
      )}
    </div>
  );
}

const MODE_ICONS = { AUTO: Sparkles, DOCUMENTS: FileText, WEB: Globe, CHAT: MessageSquare };

function ModePicker({ mode, onChange, compact, disabled }: {
  mode: Mode; onChange: (m: Mode) => void; compact?: boolean; disabled?: boolean;
}) {
  return (
    <div role="radiogroup" aria-label="Assistant mode"
         className={clsx('flex rounded-xl bg-slate-100 p-1 dark:bg-slate-800', !compact && 'w-full')}>
      {MODES.map((m) => {
        const Icon = MODE_ICONS[m.value];
        return (
          <button key={m.value} type="button" role="radio" aria-checked={mode === m.value} title={m.hint}
                  disabled={disabled}
                  onClick={() => onChange(m.value)}
                  className={clsx('flex flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition disabled:opacity-50',
                    mode === m.value ? 'bg-white text-indigo-700 shadow-sm dark:bg-slate-700 dark:text-indigo-300' : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100')}>
            <Icon className="h-4 w-4" aria-hidden />
            {m.label}
          </button>
        );
      })}
    </div>
  );
}

function StatusWarnings({ mode, status }: { mode: Mode; status?: { chatAvailable: boolean; webSearchAvailable: boolean; embeddingAvailable: boolean } }) {
  if (!status) return null;
  return (
    <>
      {!status.chatAvailable && (
        <Alert kind="warning">No AI model is configured on the server, so messages cannot be answered. Set AI_PROVIDER and AI_API_KEY.</Alert>
      )}
      {mode === 'WEB' && !status.webSearchAvailable && (
        <Alert kind="info">Live web search is not configured on this server. The assistant will tell you when it cannot access current information.</Alert>
      )}
      {mode === 'DOCUMENTS' && !status.embeddingAvailable && (
        <Alert kind="warning">No embedding model is configured, so documents cannot be searched.</Alert>
      )}
    </>
  );
}

/** Keeps the view pinned to the bottom while new content arrives, unless the user has scrolled up. */
function useAutoScroll(deps: unknown[]) {
  const [el, setEl] = useState<HTMLDivElement | null>(null);
  const pinned = useRef(true);
  useEffect(() => {
    if (!el) return;
    const onScroll = () => {
      pinned.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
    };
    el.addEventListener('scroll', onScroll);
    return () => el.removeEventListener('scroll', onScroll);
  }, [el]);
  useLayoutEffect(() => {
    if (el && pinned.current) el.scrollTop = el.scrollHeight;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [el, ...deps]);
  return setEl;
}
