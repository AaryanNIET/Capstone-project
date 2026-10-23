import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { FileText, RefreshCw, Trash2, Upload } from 'lucide-react';

import { errorMessage } from '../api/client';
import { documents, system } from '../api/endpoints';
import type { DocumentItem, DocumentStatus } from '../api/types';
import { PageBody } from '../components/Layout';
import { Spinner } from '../components/Spinner';
import { Alert, ConfirmButton, EmptyState, PageHeader } from '../components/ui';
import { formatBytes, formatDate } from '../lib/format';

const ACCEPT = '.pdf,.txt,.md,.markdown,.docx';
const ALLOWED = /\.(pdf|txt|md|markdown|docx)$/i;

export function DocumentsPage() {
  const qc = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState<string[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const status = useQuery({ queryKey: ['system-status'], queryFn: system.status, staleTime: 60_000 });
  const maxMb = status.data?.maxUploadMegabytes ?? 20;

  const list = useQuery({
    queryKey: ['documents'],
    queryFn: documents.list,
    // Poll while anything is still being processed.
    refetchInterval: (q) => (q.state.data?.items.some((d) => d.status === 'UPLOADED' || d.status === 'PROCESSING') ? 1500 : false),
  });

  const refresh = () => qc.invalidateQueries({ queryKey: ['documents'] });
  const remove = useMutation({ mutationFn: documents.remove, onSuccess: refresh });
  const reingest = useMutation({ mutationFn: documents.reingest, onSuccess: refresh });

  const upload = async (files: File[]) => {
    const problems: string[] = [];
    const accepted = files.filter((f) => {
      if (!ALLOWED.test(f.name)) problems.push(`${f.name}: only PDF, DOCX, TXT and Markdown files are supported.`);
      else if (f.size > maxMb * 1024 * 1024) problems.push(`${f.name}: larger than ${maxMb} MB.`);
      else if (f.size === 0) problems.push(`${f.name}: the file is empty.`);
      else return true;
      return false;
    });
    setErrors(problems);
    for (const file of accepted) {
      setUploading((u) => [...u, file.name]);
      try {
        await documents.upload(file);
      } catch (e) {
        setErrors((p) => [...p, `${file.name}: ${errorMessage(e)}`]);
      } finally {
        setUploading((u) => u.filter((n) => n !== file.name));
        refresh();
      }
    }
  };

  return (
    <PageBody>
      <PageHeader title="Documents"
                  description="Upload files for the assistant to search. They are split into passages, embedded and only ever visible to you." />
      {status.data && !status.data.embeddingAvailable && (
        <div className="mb-4"><Alert kind="warning">No embedding model is configured on the server, so documents cannot be processed.</Alert></div>
      )}

      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); void upload(Array.from(e.dataTransfer.files)); }}
        className={clsx('mb-6 flex flex-col items-center rounded-xl border-2 border-dashed px-6 py-10 text-center transition',
          dragging ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-950/30' : 'border-slate-300 dark:border-slate-700')}
      >
        <Upload className="mb-2 h-7 w-7 text-slate-400" aria-hidden />
        <p className="font-medium">Drop files here or{' '}
          <button className="text-indigo-600 hover:underline" onClick={() => input.current?.click()}>browse</button>
        </p>
        <p className="muted mt-1 text-sm">PDF, DOCX, TXT or Markdown · up to {maxMb} MB each</p>
        <input ref={input} type="file" multiple accept={ACCEPT} className="hidden" aria-label="Choose files to upload"
               onChange={(e) => { void upload(Array.from(e.target.files ?? [])); e.target.value = ''; }} />
        {uploading.length > 0 && (
          <p className="mt-3 flex items-center gap-2 text-sm"><Spinner />Uploading {uploading.join(', ')}…</p>
        )}
      </div>

      {errors.length > 0 && (
        <div className="mb-4"><Alert>{errors.map((e) => <div key={e}>{e}</div>)}</Alert></div>
      )}
      {(remove.isError || reingest.isError) && (
        <div className="mb-4"><Alert>{errorMessage(remove.error ?? reingest.error)}</Alert></div>
      )}

      <div className="card p-0">
        {list.isPending ? (
          <div className="flex justify-center p-8"><Spinner /></div>
        ) : list.isError ? (
          <div className="p-4"><Alert>{errorMessage(list.error)}</Alert></div>
        ) : list.data.items.length === 0 ? (
          <EmptyState icon={<FileText className="h-6 w-6" />} title="No documents yet">
            Upload a file, then ask questions about it in Documents or Auto mode.
          </EmptyState>
        ) : (
          <ul className="divide-y divide-slate-200 dark:divide-slate-800">
            {list.data.items.map((d) => (
              <DocumentRow key={d.id} d={d}
                           onDelete={() => remove.mutate(d.id)}
                           onReingest={() => reingest.mutate(d.id)} />
            ))}
          </ul>
        )}
      </div>
    </PageBody>
  );
}

const STATUS_STYLE: Record<DocumentStatus, string> = {
  UPLOADED: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  PROCESSING: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  READY: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
  FAILED: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
};

function DocumentRow({ d, onDelete, onReingest }: { d: DocumentItem; onDelete: () => void; onReingest: () => void }) {
  const working = d.status === 'UPLOADED' || d.status === 'PROCESSING';
  return (
    <li className="flex flex-wrap items-center gap-4 px-5 py-4">
      <FileText className="h-5 w-5 shrink-0 text-slate-400" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium" title={d.filename}>{d.filename}</p>
        <p className="muted text-xs">
          {d.fileType} · {formatBytes(d.sizeBytes)}
          {d.pageCount != null && ` · ${d.pageCount} pages`}
          {d.status === 'READY' && ` · ${d.chunkCount} passages`}
          {' · '}uploaded {formatDate(d.createdAt)}
        </p>
        {working && (
          <div className="mt-2 h-1.5 w-full max-w-xs overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800"
               role="progressbar" aria-valuenow={d.progress} aria-valuemin={0} aria-valuemax={100}>
            <div className="h-full bg-indigo-500 transition-all" style={{ width: `${Math.max(5, d.progress)}%` }} />
          </div>
        )}
        {d.status === 'FAILED' && d.errorMessage && <p className="mt-1 text-xs text-red-600">{d.errorMessage}</p>}
      </div>
      <span className={clsx('badge', STATUS_STYLE[d.status])}>
        {working && <Spinner className="h-3 w-3" />}
        {d.status === 'PROCESSING' ? `Processing ${d.progress}%` : d.status.charAt(0) + d.status.slice(1).toLowerCase()}
      </span>
      <div className="flex gap-2">
        {(d.status === 'FAILED' || d.status === 'READY') && (
          <button className="btn-ghost p-2" title="Process again" aria-label={`Process ${d.filename} again`} onClick={onReingest}>
            <RefreshCw className="h-4 w-4" />
          </button>
        )}
        <ConfirmButton onConfirm={onDelete} className="btn-ghost p-2" confirmLabel="Delete" title={`Delete ${d.filename}`}>
          <Trash2 className="h-4 w-4" aria-label={`Delete ${d.filename}`} />
        </ConfirmButton>
      </div>
    </li>
  );
}
