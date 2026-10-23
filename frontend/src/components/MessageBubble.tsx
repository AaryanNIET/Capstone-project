import { useMemo, useState } from 'react';
import clsx from 'clsx';
import { AlertCircle, Brain, Check, Copy, FileText, Globe, RefreshCw, ThumbsDown, ThumbsUp } from 'lucide-react';

import type { Message, Rating } from '../api/types';
import { Markdown } from './Markdown';
import { SourceList } from './SourceList';

interface Props {
  message: Message;
  canRegenerate?: boolean;
  onRegenerate?: () => void;
  onRate?: (rating: Rating | null) => void;
}

export function MessageBubble({ message: m, canRegenerate, onRegenerate, onRate }: Props) {
  if (m.role === 'USER') {
    return (
      <div className="flex justify-end">
        <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-md bg-indigo-600 px-4 py-2.5 text-[15px] text-white">
          {m.content}
        </div>
      </div>
    );
  }
  return <AssistantMessage m={m} canRegenerate={canRegenerate} onRegenerate={onRegenerate} onRate={onRate} />;
}

function AssistantMessage({ m, canRegenerate, onRegenerate, onRate }: { m: Message } & Omit<Props, 'message'>) {
  const [highlighted, setHighlighted] = useState<number | null>(null);
  const [copied, setCopied] = useState(false);
  const sources = m.sources ?? [];
  const citations = useMemo(() => new Set(sources.map((s) => s.citation)), [sources]);

  const focusSource = (n: number) => {
    setHighlighted(n);
    document.getElementById(`source-${m.id}-${n}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  };

  return (
    <div className="group">
      <UsageBadges m={m} />
      {m.content ? (
        <Markdown content={m.content} citations={citations} onCitation={focusSource} />
      ) : m.status === 'CANCELLED' ? (
        <p className="muted text-sm italic">Stopped before an answer was written.</p>
      ) : null}
      {m.status === 'CANCELLED' && m.content && <p className="muted mt-2 text-xs">Stopped before finishing.</p>}
      {m.status === 'FAILED' && (
        <div className="mt-2 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          {m.errorMessage ?? 'The response could not be generated.'}
        </div>
      )}
      <SourceList sources={sources} highlighted={highlighted} messageId={m.id} />
      <div className="mt-2 flex items-center gap-1 text-slate-500 opacity-100 transition sm:opacity-0 sm:group-hover:opacity-100 sm:focus-within:opacity-100">
        <IconButton label={copied ? 'Copied' : 'Copy answer'} onClick={async () => {
          await navigator.clipboard.writeText(m.content);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        }}>
          {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
        </IconButton>
        {onRate && (
          <>
            <IconButton label="Good answer" active={m.feedback === 'UP'} onClick={() => onRate(m.feedback === 'UP' ? null : 'UP')}>
              <ThumbsUp className="h-4 w-4" />
            </IconButton>
            <IconButton label="Bad answer" active={m.feedback === 'DOWN'} onClick={() => onRate(m.feedback === 'DOWN' ? null : 'DOWN')}>
              <ThumbsDown className="h-4 w-4" />
            </IconButton>
          </>
        )}
        {canRegenerate && onRegenerate && (
          <IconButton label="Regenerate answer" onClick={onRegenerate}>
            <RefreshCw className="h-4 w-4" />
          </IconButton>
        )}
      </div>
    </div>
  );
}

export function UsageBadges({ m }: { m: Pick<Message, 'usedDocuments' | 'usedWeb' | 'usedMemory'> }) {
  if (!m.usedDocuments && !m.usedWeb && !m.usedMemory) return null;
  return (
    <div className="mb-2 flex flex-wrap gap-1.5">
      {m.usedDocuments && <span className="badge bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"><FileText className="h-3 w-3" />Your documents</span>}
      {m.usedWeb && <span className="badge bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300"><Globe className="h-3 w-3" />Live web</span>}
      {m.usedMemory && <span className="badge bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300"><Brain className="h-3 w-3" />Memory</span>}
    </div>
  );
}

function IconButton({ label, onClick, active, children }: {
  label: string; onClick: () => void; active?: boolean; children: React.ReactNode;
}) {
  return (
    <button type="button" title={label} aria-label={label} aria-pressed={active} onClick={onClick}
            className={clsx('rounded-md p-1.5 hover:bg-slate-200 dark:hover:bg-slate-800',
              active && 'text-indigo-600 dark:text-indigo-400')}>
      {children}
    </button>
  );
}
