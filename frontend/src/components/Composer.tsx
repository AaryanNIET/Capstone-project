import { useEffect, useRef, useState } from 'react';
import { ArrowUp, Square } from 'lucide-react';

const MAX = 8000;

export function Composer({ onSend, onStop, busy, disabled, placeholder }: {
  onSend: (text: string) => void;
  onStop: () => void;
  busy: boolean;
  disabled?: boolean;
  placeholder?: string;
}) {
  const [text, setText] = useState('');
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 240) + 'px';
  }, [text]);

  useEffect(() => {
    if (!busy) ref.current?.focus();
  }, [busy]);

  const submit = () => {
    const value = text.trim();
    if (!value || busy || disabled || value.length > MAX) return;
    onSend(value);
    setText('');
  };

  return (
    <form className="mx-auto w-full max-w-3xl px-4 pb-4" onSubmit={(e) => { e.preventDefault(); submit(); }}>
      <div className="flex items-end gap-2 rounded-2xl border border-slate-300 bg-white p-2 shadow-sm focus-within:border-indigo-500 dark:border-slate-700 dark:bg-slate-900">
        <textarea
          ref={ref}
          rows={1}
          value={text}
          disabled={disabled}
          aria-label="Message"
          placeholder={placeholder ?? 'Ask anything…'}
          className="max-h-60 min-h-[40px] flex-1 resize-none bg-transparent px-2 py-2 text-[15px] outline-none placeholder:text-slate-400 focus-visible:ring-0 focus-visible:ring-offset-0"
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              submit();
            }
          }}
        />
        {busy ? (
          <button type="button" className="btn-secondary h-10 w-10 rounded-xl p-0" onClick={onStop} aria-label="Stop generating" title="Stop">
            <Square className="h-4 w-4 fill-current" />
          </button>
        ) : (
          <button type="submit" className="btn-primary h-10 w-10 rounded-xl p-0" aria-label="Send"
                  disabled={!text.trim() || disabled || text.length > MAX}>
            <ArrowUp className="h-5 w-5" />
          </button>
        )}
      </div>
      <div className="muted mt-1.5 flex justify-between px-2 text-xs">
        <span>Enter to send · Shift+Enter for a new line. Answers can be wrong; check the sources.</span>
        {text.length > MAX * 0.8 && <span className={text.length > MAX ? 'text-red-600' : ''}>{text.length}/{MAX}</span>}
      </div>
    </form>
  );
}
