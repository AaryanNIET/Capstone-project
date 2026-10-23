import { rawRequest } from './client';
import type { Message, Mode, Source } from './types';

export interface SseEvent {
  event: string;
  data: string;
}

/**
 * Splits a buffer of Server-Sent Events text into complete events and the unfinished remainder. Supports multi-line
 * data fields, comments and CRLF line endings.
 */
export function parseSseBuffer(buffer: string): { events: SseEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n?/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const events: SseEvent[] = [];
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue;
      const colon = line.indexOf(':');
      const field = colon === -1 ? line : line.slice(0, colon);
      let value = colon === -1 ? '' : line.slice(colon + 1);
      if (value.startsWith(' ')) value = value.slice(1);
      if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
    }
    if (data.length) events.push({ event, data: data.join('\n') });
  }
  return { events, rest };
}

export interface StreamMeta {
  conversationId: string;
  title: string;
  mode: Mode;
  regenerate: boolean;
  replacesMessageId?: string;
  userMessage: Message;
}

export interface ToolEvent {
  tool: string;
  label?: string;
  state: 'started' | 'finished';
  status?: 'OK' | 'ERROR' | 'REJECTED' | 'TIMEOUT';
  durationMs?: number;
}

export interface StreamHandlers {
  onMeta?: (meta: StreamMeta) => void;
  onStatus?: (stage: string, label: string) => void;
  onTool?: (tool: ToolEvent) => void;
  onContent?: (delta: string) => void;
  onReset?: () => void;
  onSources?: (sources: Source[]) => void;
  onDone?: (message: Message) => void;
  onError?: (code: string, message: string) => void;
}

export interface SendBody {
  content?: string;
  mode?: Mode;
  regenerate?: boolean;
}

/** POSTs a message and dispatches the streamed events. Resolves when the stream ends. */
export async function streamChat(conversationId: string, body: SendBody, handlers: StreamHandlers,
                                 signal?: AbortSignal): Promise<void> {
  const res = await rawRequest(`/api/chat/${conversationId}/stream`, {
    method: 'POST',
    body,
    headers: { Accept: 'text/event-stream' },
    signal,
  });
  if (!res.body) throw new Error('Streaming is not supported by this browser');
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let finished = false;
  while (!finished) {
    const { value, done } = await reader.read();
    if (done) {
      buffer += decoder.decode() + '\n\n';
      finished = true;
    } else {
      buffer += decoder.decode(value, { stream: true });
    }
    const { events, rest } = parseSseBuffer(buffer);
    buffer = rest;
    for (const e of events) dispatch(e, handlers);
  }
}

function dispatch(e: SseEvent, h: StreamHandlers) {
  let data: any;
  try {
    data = JSON.parse(e.data);
  } catch {
    return;
  }
  switch (e.event) {
    case 'meta': h.onMeta?.(data); break;
    case 'status': h.onStatus?.(data.stage, data.label); break;
    case 'tool': h.onTool?.(data); break;
    case 'content': h.onContent?.(data.delta); break;
    case 'reset': h.onReset?.(); break;
    case 'sources': h.onSources?.(data.sources ?? []); break;
    case 'done': h.onDone?.(data); break;
    case 'error': h.onError?.(data.code, data.message); break;
  }
}
