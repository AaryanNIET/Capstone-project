import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Brain, Check, Pencil, Plus, Trash2, X } from 'lucide-react';

import { errorMessage } from '../api/client';
import { memories } from '../api/endpoints';
import type { MemoryCategory, MemoryItem } from '../api/types';
import { PageBody } from '../components/Layout';
import { Spinner } from '../components/Spinner';
import { Alert, ConfirmButton, EmptyState, PageHeader, Toggle } from '../components/ui';
import { formatDate } from '../lib/format';

const CATEGORIES: { value: MemoryCategory; label: string }[] = [
  { value: 'PREFERENCE', label: 'Preference' },
  { value: 'PROJECT', label: 'Project' },
  { value: 'FACT', label: 'Fact' },
  { value: 'GOAL', label: 'Goal' },
  { value: 'INSTRUCTION', label: 'Instruction' },
];

const ORIGIN_LABEL = { EXPLICIT: 'You asked to remember', EXTRACTED: 'Learned from chat', MANUAL: 'Added manually' };

export function MemoryPage() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState<MemoryCategory | ''>('');
  const settings = useQuery({ queryKey: ['memory-settings'], queryFn: memories.settings });
  const list = useQuery({ queryKey: ['memories', filter], queryFn: () => memories.list(filter || undefined) });
  const refresh = () => qc.invalidateQueries({ queryKey: ['memories'] });

  const updateSettings = useMutation({
    mutationFn: memories.updateSettings,
    onSuccess: (s) => {
      qc.setQueryData(['memory-settings'], s);
      qc.invalidateQueries({ queryKey: ['me'] });
    },
  });
  const clear = useMutation({ mutationFn: memories.clear, onSuccess: refresh });

  return (
    <PageBody>
      <PageHeader title="Memory"
                  description="What the assistant remembers about you across conversations. You can edit or delete anything here." />

      <div className="card mb-6 space-y-4">
        {settings.data ? (
          <>
            <Toggle label="Use long-term memory" checked={settings.data.enabled} disabled={updateSettings.isPending}
                    description="When off, nothing new is saved and stored memories are not used in answers."
                    onChange={(enabled) => updateSettings.mutate({ enabled })} />
            <Toggle label="Delete memories with their conversation" checked={settings.data.deleteWithConversation}
                    disabled={updateSettings.isPending}
                    description="When you delete a conversation, also delete the memories learned from it."
                    onChange={(deleteWithConversation) => updateSettings.mutate({ deleteWithConversation })} />
          </>
        ) : <Spinner />}
        {updateSettings.isError && <Alert>{errorMessage(updateSettings.error)}</Alert>}
      </div>

      <AddMemory disabled={settings.data?.enabled === false} onAdded={refresh} />

      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <select className="input w-auto" value={filter} aria-label="Filter by category"
                onChange={(e) => setFilter(e.target.value as MemoryCategory | '')}>
          <option value="">All categories</option>
          {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        {(list.data?.items.length ?? 0) > 0 && (
          <ConfirmButton onConfirm={() => clear.mutate()} confirmLabel="Delete all memories?">
            <Trash2 className="h-4 w-4" /> Clear all
          </ConfirmButton>
        )}
      </div>

      <div className="card p-0">
        {list.isPending ? (
          <div className="flex justify-center p-8"><Spinner /></div>
        ) : list.isError ? (
          <div className="p-4"><Alert>{errorMessage(list.error)}</Alert></div>
        ) : list.data.items.length === 0 ? (
          <EmptyState icon={<Brain className="h-6 w-6" />} title="Nothing remembered yet">
            Tell the assistant things like “Remember that I prefer short answers” or “I'm working on a capstone project”.
          </EmptyState>
        ) : (
          <ul className="divide-y divide-slate-200 dark:divide-slate-800">
            {list.data.items.map((m) => <MemoryRow key={m.id} m={m} onChanged={refresh} />)}
          </ul>
        )}
      </div>
    </PageBody>
  );
}

function AddMemory({ disabled, onAdded }: { disabled: boolean; onAdded: () => void }) {
  const [content, setContent] = useState('');
  const [category, setCategory] = useState<MemoryCategory>('PREFERENCE');
  const add = useMutation({
    mutationFn: () => memories.create(content.trim(), category),
    onSuccess: () => {
      setContent('');
      onAdded();
    },
  });
  return (
    <form className="card mb-6 space-y-3" onSubmit={(e) => { e.preventDefault(); if (content.trim()) add.mutate(); }}>
      <h2 className="font-medium">Add a memory</h2>
      {disabled && <Alert kind="info">Memory is turned off, so new memories cannot be added.</Alert>}
      <div className="flex flex-col gap-2 sm:flex-row">
        <input className="input flex-1" placeholder="e.g. User prefers examples in Java" value={content} maxLength={1000}
               aria-label="Memory content" disabled={disabled} onChange={(e) => setContent(e.target.value)} />
        <select className="input sm:w-40" value={category} aria-label="Category" disabled={disabled}
                onChange={(e) => setCategory(e.target.value as MemoryCategory)}>
          {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <button className="btn-primary" disabled={disabled || !content.trim() || add.isPending}>
          {add.isPending ? <Spinner /> : <Plus className="h-4 w-4" />} Add
        </button>
      </div>
      {add.isError && <Alert>{errorMessage(add.error)}</Alert>}
    </form>
  );
}

function MemoryRow({ m, onChanged }: { m: MemoryItem; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(m.content);
  const [category, setCategory] = useState(m.category);
  const save = useMutation({
    mutationFn: () => memories.update(m.id, { content: content.trim(), category }),
    onSuccess: () => { setEditing(false); onChanged(); },
  });
  const remove = useMutation({ mutationFn: () => memories.remove(m.id), onSuccess: onChanged });

  return (
    <li className="px-5 py-4">
      {editing ? (
        <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
          <textarea className="input" rows={2} value={content} maxLength={1000} aria-label="Memory content"
                    onChange={(e) => setContent(e.target.value)} />
          <div className="flex gap-2">
            <select className="input w-40" value={category} aria-label="Category" onChange={(e) => setCategory(e.target.value as MemoryCategory)}>
              {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
            <button className="btn-primary" disabled={save.isPending || content.trim().length < 3}><Check className="h-4 w-4" />Save</button>
            <button type="button" className="btn-ghost" onClick={() => { setEditing(false); setContent(m.content); setCategory(m.category); }}>
              <X className="h-4 w-4" />Cancel
            </button>
          </div>
          {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
        </form>
      ) : (
        <div className="flex items-start gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-sm">{m.content}</p>
            <p className="muted mt-1 flex flex-wrap gap-x-2 text-xs">
              <span className="badge bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300">{m.category.toLowerCase()}</span>
              <span>{ORIGIN_LABEL[m.origin]}</span>
              <span>· updated {formatDate(m.updatedAt)}</span>
              {m.lastUsedAt && <span>· last used {formatDate(m.lastUsedAt)}</span>}
            </p>
          </div>
          <button className="btn-ghost p-2" title="Edit" aria-label="Edit memory" onClick={() => setEditing(true)}>
            <Pencil className="h-4 w-4" />
          </button>
          <ConfirmButton onConfirm={() => remove.mutate()} className="btn-ghost p-2" confirmLabel="Delete" title="Delete memory">
            <Trash2 className="h-4 w-4" aria-label="Delete memory" />
          </ConfirmButton>
        </div>
      )}
    </li>
  );
}
