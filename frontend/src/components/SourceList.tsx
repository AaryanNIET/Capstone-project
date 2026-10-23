import { useState } from 'react';
import clsx from 'clsx';
import { Brain, ChevronDown, ExternalLink, FileText, Globe } from 'lucide-react';

import type { Source } from '../api/types';
import { formatDate } from '../lib/format';

const ICONS = { DOCUMENT: FileText, WEB: Globe, MEMORY: Brain };

export function SourceList({ sources, highlighted, messageId }: {
  sources: Source[];
  highlighted?: number | null;
  messageId: string;
}) {
  const [showAll, setShowAll] = useState(false);
  const cited = sources.filter((s) => s.cited);
  // Show what the answer cited. Without citations, show what tools retrieved; memories in the prompt are already
  // signalled by the Memory badge. Everything else is one click away.
  const preferred = cited.length > 0 ? cited : sources.filter((s) => s.type !== 'MEMORY');
  const visible = showAll ? sources : preferred;
  if (sources.length === 0) return null;
  if (visible.length === 0 && !showAll) {
    return (
      <button className="muted mt-2 inline-flex items-center gap-1 text-xs hover:text-slate-900 dark:hover:text-slate-100"
              onClick={() => setShowAll(true)}>
        {sources.length} {sources.length === 1 ? 'memory' : 'memories'} available to this answer
        <ChevronDown className="h-3 w-3" />
      </button>
    );
  }

  return (
    <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50/70 dark:border-slate-800 dark:bg-slate-900/60">
      <div className="flex items-center justify-between px-3 py-2 text-xs font-medium text-slate-600 dark:text-slate-400">
        <span>Sources{cited.length > 0 ? ` · ${cited.length} cited` : ''}</span>
        {preferred.length < sources.length && (
          <button className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  onClick={() => setShowAll((v) => !v)}>
            {showAll ? (cited.length > 0 ? 'Show cited only' : 'Show fewer') : `Show all ${sources.length}`}
            <ChevronDown className={clsx('h-3 w-3 transition', showAll && 'rotate-180')} />
          </button>
        )}
      </div>
      <ol className="divide-y divide-slate-200 dark:divide-slate-800">
        {visible.map((s) => <SourceRow key={s.citation} source={s} active={highlighted === s.citation} messageId={messageId} />)}
      </ol>
    </div>
  );
}

function SourceRow({ source: s, active, messageId }: { source: Source; active: boolean; messageId: string }) {
  const Icon = ICONS[s.type];
  const detail = s.type === 'DOCUMENT'
    ? [s.pageNumber != null ? `page ${s.pageNumber}` : null, s.chunkIndex != null ? `passage ${s.chunkIndex + 1}` : null].filter(Boolean).join(' · ')
    : s.type === 'WEB' ? `retrieved ${formatDate(s.retrievedAt)}` : 'long-term memory';
  const host = s.url ? safeHost(s.url) : null;
  return (
    <li id={`source-${messageId}-${s.citation}`}
        className={clsx('flex gap-3 px-3 py-2 text-sm transition', active && 'bg-indigo-50 dark:bg-indigo-950/50')}>
      <span className="mt-0.5 flex h-5 min-w-5 items-center justify-center rounded bg-slate-200 px-1 text-[11px] font-semibold dark:bg-slate-800">
        {s.citation}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <Icon className="h-3.5 w-3.5 shrink-0 text-slate-500" aria-hidden />
          {s.url ? (
            <a href={s.url} target="_blank" rel="noopener noreferrer nofollow"
               className="truncate font-medium text-indigo-700 hover:underline dark:text-indigo-300">
              {s.title}
            </a>
          ) : (
            <span className="truncate font-medium">{s.title}</span>
          )}
          {s.url && <ExternalLink className="h-3 w-3 shrink-0 text-slate-400" aria-hidden />}
        </div>
        <div className="muted text-xs">{[host, detail].filter(Boolean).join(' · ')}</div>
        {s.snippet && <p className="mt-1 line-clamp-3 text-xs text-slate-600 dark:text-slate-400">{s.snippet}</p>}
      </div>
    </li>
  );
}

function safeHost(url: string): string | null {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return null;
  }
}
