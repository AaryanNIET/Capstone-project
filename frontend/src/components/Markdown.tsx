import { memo, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import { linkCitations } from '../lib/format';

interface Props {
  content: string;
  citations?: Set<number>;
  onCitation?: (n: number) => void;
}

/**
 * Renders assistant Markdown. Raw HTML is not rendered (react-markdown escapes it), unsafe URL schemes are stripped by
 * the default URL transform, and links open in a new tab without referrer. Citation markers become clickable chips.
 */
export const Markdown = memo(function Markdown({ content, citations, onCitation }: Props) {
  const text = useMemo(() => (citations?.size ? linkCitations(content, citations) : content), [content, citations]);
  return (
    <div className="prose prose-slate max-w-none text-[15px] leading-relaxed dark:prose-invert prose-pre:bg-slate-900 prose-pre:text-slate-100 prose-code:before:content-none prose-code:after:content-none">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a({ href, children }) {
            const cite = href?.match(/^#cite-(\d+)$/);
            if (cite) {
              const n = Number(cite[1]);
              return (
                <button
                  type="button"
                  onClick={() => onCitation?.(n)}
                  className="mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded bg-indigo-100 px-1 align-text-top text-[11px] font-semibold text-indigo-700 no-underline hover:bg-indigo-200 dark:bg-indigo-900/60 dark:text-indigo-200"
                  aria-label={`Source ${n}`}
                >
                  {n}
                </button>
              );
            }
            return (
              <a href={href} target="_blank" rel="noopener noreferrer nofollow">
                {children}
              </a>
            );
          },
          table({ children }) {
            return <div className="overflow-x-auto"><table>{children}</table></div>;
          },
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
});
