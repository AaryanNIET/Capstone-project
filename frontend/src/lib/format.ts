import type { Mode } from '../api/types';

export const MODES: { value: Mode; label: string; hint: string }[] = [
  { value: 'AUTO', label: 'Auto', hint: 'The assistant picks documents, web or memory as needed' },
  { value: 'DOCUMENTS', label: 'Documents', hint: 'Answers grounded in your uploaded files' },
  { value: 'WEB', label: 'Web', hint: 'Live web search for current information' },
  { value: 'CHAT', label: 'Chat', hint: 'General conversation, no retrieval' },
];

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDate(iso?: string): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.round(diff / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

/** Turns citation markers like [2] into links the Markdown renderer shows as chips. Code is left untouched. */
export function linkCitations(markdown: string, valid: Set<number>): string {
  const code = /```[\s\S]*?(?:```|$)|`[^`\n]+`/g;
  let out = '';
  let last = 0;
  for (const m of markdown.matchAll(code)) {
    out += replaceInProse(markdown.slice(last, m.index), valid) + m[0];
    last = (m.index ?? 0) + m[0].length;
  }
  return out + replaceInProse(markdown.slice(last), valid);
}

function replaceInProse(text: string, valid: Set<number>): string {
  return text.replace(/\[(\d{1,3})\](?![(:])/g, (whole, n: string) =>
    valid.has(Number(n)) ? `[\\[${n}\\]](#cite-${n})` : whole);
}
