import { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Archive, ArchiveRestore, Check, MessageSquarePlus, Pencil, Search, Trash2, X } from 'lucide-react';

import { conversations } from '../api/endpoints';
import type { ConversationItem } from '../api/types';
import { relativeTime } from '../lib/format';
import { Spinner } from './Spinner';

export function ConversationSidebar({ activeId, onNavigate }: { activeId?: string; onNavigate?: () => void }) {
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [archived, setArchived] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query.trim()), 250);
    return () => clearTimeout(t);
  }, [query]);

  const list = useQuery({
    queryKey: ['conversations', debounced, archived],
    queryFn: () => conversations.list(debounced, archived),
  });

  return (
    <aside className="flex h-full w-full flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div className="space-y-2 p-3">
        <button className="btn-primary w-full" onClick={() => { navigate('/chat'); onNavigate?.(); }}>
          <MessageSquarePlus className="h-4 w-4" aria-hidden />
          New chat
        </button>
        <div className="relative">
          <Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-slate-400" aria-hidden />
          <input className="input pl-8" placeholder="Search conversations" value={query} aria-label="Search conversations"
                 onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div className="flex rounded-lg bg-slate-100 p-0.5 text-xs dark:bg-slate-800" role="tablist">
          {[false, true].map((a) => (
            <button key={String(a)} role="tab" aria-selected={archived === a} onClick={() => setArchived(a)}
                    className={clsx('flex-1 rounded-md py-1 font-medium', archived === a
                      ? 'bg-white shadow-sm dark:bg-slate-700' : 'text-slate-500')}>
              {a ? 'Archived' : 'Active'}
            </button>
          ))}
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        {list.isPending && <div className="flex justify-center p-4"><Spinner /></div>}
        {list.isError && <p className="p-3 text-sm text-red-600">Could not load conversations.</p>}
        {list.data?.items.length === 0 && (
          <p className="muted p-3 text-sm">{debounced ? 'No matching conversations.' : archived ? 'No archived conversations.' : 'No conversations yet.'}</p>
        )}
        <ul className="space-y-0.5">
          {list.data?.items.map((c) => (
            <ConversationRow key={c.id} c={c} active={c.id === activeId} onNavigate={onNavigate} />
          ))}
        </ul>
      </div>
    </aside>
  );
}

function ConversationRow({ c, active, onNavigate }: { c: ConversationItem; active: boolean; onNavigate?: () => void }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(c.title);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const refresh = () => qc.invalidateQueries({ queryKey: ['conversations'] });
  const update = useMutation({
    mutationFn: (body: { title?: string; archived?: boolean }) => conversations.update(c.id, body),
    onSuccess: () => {
      refresh();
      qc.invalidateQueries({ queryKey: ['conversation', c.id] });
    },
  });
  const remove = useMutation({
    mutationFn: () => conversations.remove(c.id),
    onSuccess: () => {
      refresh();
      qc.removeQueries({ queryKey: ['conversation', c.id] });
      if (active) navigate('/chat');
    },
  });

  if (editing) {
    return (
      <li>
        <form className="flex items-center gap-1 p-1" onSubmit={(e) => {
          e.preventDefault();
          if (title.trim()) update.mutate({ title: title.trim() });
          setEditing(false);
        }}>
          <input className="input py-1" value={title} maxLength={200} autoFocus aria-label="Conversation title"
                 onChange={(e) => setTitle(e.target.value)} onKeyDown={(e) => e.key === 'Escape' && setEditing(false)} />
          <button className="btn-ghost p-1.5" aria-label="Save title"><Check className="h-4 w-4" /></button>
          <button type="button" className="btn-ghost p-1.5" aria-label="Cancel" onClick={() => setEditing(false)}><X className="h-4 w-4" /></button>
        </form>
      </li>
    );
  }

  return (
    <li className="group relative">
      <NavLink to={`/chat/${c.id}`} onClick={onNavigate}
               className={clsx('block rounded-lg px-3 py-2 pr-20 text-sm',
                 active ? 'bg-indigo-50 text-indigo-900 dark:bg-indigo-950/60 dark:text-indigo-100' : 'hover:bg-slate-100 dark:hover:bg-slate-800')}>
        <span className="block truncate font-medium">{c.title}</span>
        <span className="muted text-xs">{relativeTime(c.updatedAt)} · {c.mode.toLowerCase()}</span>
      </NavLink>
      <div className="absolute right-1 top-1.5 hidden items-center group-hover:flex group-focus-within:flex">
        {confirmDelete ? (
          <button className="btn-danger px-2 py-1 text-xs" onClick={() => remove.mutate()} onBlur={() => setConfirmDelete(false)} autoFocus>
            Delete?
          </button>
        ) : (
          <>
            <button className="btn-ghost p-1.5" title="Rename" aria-label="Rename" onClick={() => { setTitle(c.title); setEditing(true); }}>
              <Pencil className="h-3.5 w-3.5" />
            </button>
            <button className="btn-ghost p-1.5" title={c.archived ? 'Unarchive' : 'Archive'} aria-label={c.archived ? 'Unarchive' : 'Archive'}
                    onClick={() => update.mutate({ archived: !c.archived })}>
              {c.archived ? <ArchiveRestore className="h-3.5 w-3.5" /> : <Archive className="h-3.5 w-3.5" />}
            </button>
            <button className="btn-ghost p-1.5 hover:text-red-600" title="Delete" aria-label="Delete" onClick={() => setConfirmDelete(true)}>
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </>
        )}
      </div>
    </li>
  );
}
