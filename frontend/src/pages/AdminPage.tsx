import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';

import { errorMessage } from '../api/client';
import { admin } from '../api/endpoints';
import type { AdminStats } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageBody } from '../components/Layout';
import { Spinner } from '../components/Spinner';
import { Alert, PageHeader } from '../components/ui';
import { formatDate } from '../lib/format';

type Tab = 'overview' | 'users' | 'executions' | 'audit';

export function AdminPage() {
  const [tab, setTab] = useState<Tab>('overview');
  return (
    <PageBody>
      <PageHeader title="Administration" description="Usage, accounts and agent activity. Message content is never shown here." />
      <div className="mb-6 flex gap-1 border-b border-slate-200 dark:border-slate-800" role="tablist">
        {(['overview', 'users', 'executions', 'audit'] as Tab[]).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
                  className={clsx('-mb-px border-b-2 px-3 py-2 text-sm font-medium capitalize',
                    tab === t ? 'border-indigo-600 text-indigo-700 dark:text-indigo-300' : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200')}>
            {t === 'audit' ? 'Audit log' : t}
          </button>
        ))}
      </div>
      {tab === 'overview' && <Overview />}
      {tab === 'users' && <Users />}
      {tab === 'executions' && <Executions />}
      {tab === 'audit' && <Audit />}
    </PageBody>
  );
}

function Overview() {
  const q = useQuery({ queryKey: ['admin', 'stats'], queryFn: admin.stats, refetchInterval: 15_000 });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <Alert>{errorMessage(q.error)}</Alert>;
  const s: AdminStats = q.data;
  const total = s.executionsCompleted + s.executionsFailed + s.executionsTimedOut + s.executionsCancelled;
  const tiles = [
    { label: 'Users', value: s.users },
    { label: 'Conversations', value: s.conversations },
    { label: 'Questions asked', value: s.userMessages },
    { label: 'Documents ready', value: s.documentsReady, sub: `${s.documentsProcessing} processing · ${s.documentsFailed} failed` },
    { label: 'Agent runs (24h)', value: s.executionsLast24h,
      sub: s.averageDurationMsLast24h ? `avg ${(s.averageDurationMsLast24h / 1000).toFixed(1)} s` : undefined },
    { label: 'Success rate', value: total ? `${Math.round((s.executionsCompleted / total) * 100)}%` : '–',
      sub: `${s.executionsFailed} failed · ${s.executionsTimedOut} timed out · ${s.executionsCancelled} stopped` },
    { label: 'Feedback', value: `${s.feedbackUp} 👍 / ${s.feedbackDown} 👎` },
    { label: 'Active runs', value: s.activeRuns },
  ];
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      {tiles.map((t) => (
        <div key={t.label} className="card">
          <p className="muted text-sm">{t.label}</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums">{t.value}</p>
          {t.sub && <p className="muted mt-1 text-xs">{t.sub}</p>}
        </div>
      ))}
    </div>
  );
}

function Users() {
  const qc = useQueryClient();
  const { user: me } = useAuth();
  const q = useQuery({ queryKey: ['admin', 'users'], queryFn: admin.users });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Parameters<typeof admin.updateUser>[1] }) => admin.updateUser(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <Alert>{errorMessage(q.error)}</Alert>;
  return (
    <div className="space-y-3">
      {update.isError && <Alert>{errorMessage(update.error)}</Alert>}
      <Table head={['Name', 'Email', 'Role', 'Status', 'Joined', '']}>
        {q.data.items.map((u) => (
          <tr key={u.id}>
            <td>{u.name}</td>
            <td className="break-all">{u.email}</td>
            <td>{u.role.toLowerCase()}</td>
            <td>{u.enabled ? <span className="text-emerald-600">active</span> : <span className="text-red-600">disabled</span>}</td>
            <td className="whitespace-nowrap">{formatDate(u.createdAt)}</td>
            <td className="whitespace-nowrap text-right">
              {u.id !== me?.id && (
                <div className="flex justify-end gap-2">
                  <button className="btn-secondary px-2 py-1 text-xs"
                          onClick={() => update.mutate({ id: u.id, body: { role: u.role === 'ADMIN' ? 'USER' : 'ADMIN' } })}>
                    {u.role === 'ADMIN' ? 'Make user' : 'Make admin'}
                  </button>
                  <button className={clsx('px-2 py-1 text-xs', u.enabled ? 'btn-danger' : 'btn-secondary')}
                          onClick={() => update.mutate({ id: u.id, body: { enabled: !u.enabled } })}>
                    {u.enabled ? 'Disable' : 'Enable'}
                  </button>
                </div>
              )}
            </td>
          </tr>
        ))}
      </Table>
    </div>
  );
}

function Executions() {
  const q = useQuery({ queryKey: ['admin', 'executions'], queryFn: admin.executions, refetchInterval: 15_000 });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <Alert>{errorMessage(q.error)}</Alert>;
  return (
    <Table head={['Started', 'Mode', 'Model', 'Status', 'Iterations', 'Tool calls', 'Duration', 'Error']}>
      {q.data.items.map((e) => (
        <tr key={e.id}>
          <td className="whitespace-nowrap">{formatDate(e.startedAt)}</td>
          <td>{e.mode.toLowerCase()}</td>
          <td>{e.model}</td>
          <td className={clsx(e.status === 'COMPLETED' ? 'text-emerald-600' : e.status === 'CANCELLED' ? 'text-slate-500' : 'text-red-600')}>
            {e.status.toLowerCase().replace('_', ' ')}
          </td>
          <td className="tabular-nums">{e.iterations}</td>
          <td className="tabular-nums">{e.toolCallCount}</td>
          <td className="tabular-nums">{e.durationMs != null ? `${(e.durationMs / 1000).toFixed(1)} s` : ''}</td>
          <td className="max-w-xs truncate" title={e.errorMessage}>{e.errorMessage}</td>
        </tr>
      ))}
    </Table>
  );
}

function Audit() {
  const q = useQuery({ queryKey: ['admin', 'audit'], queryFn: admin.auditLogs });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <Alert>{errorMessage(q.error)}</Alert>;
  return (
    <Table head={['Time', 'Action', 'Actor', 'Target', 'Details']}>
      {q.data.items.map((a) => (
        <tr key={a.id}>
          <td className="whitespace-nowrap">{formatDate(a.createdAt)}</td>
          <td className="font-mono text-xs">{a.action}</td>
          <td className="break-all">{a.actorEmail ?? '–'}</td>
          <td className="font-mono text-xs">{a.targetType ? `${a.targetType} ${a.targetId?.slice(0, 8) ?? ''}` : ''}</td>
          <td>{a.details}</td>
        </tr>
      ))}
    </Table>
  );
}

function Table({ head, children }: { head: string[]; children: React.ReactNode }) {
  return (
    <div className="card overflow-x-auto p-0">
      <table className="w-full text-left text-sm [&_td]:px-4 [&_td]:py-2.5 [&_tbody_tr]:border-t [&_tbody_tr]:border-slate-200 dark:[&_tbody_tr]:border-slate-800">
        <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800/50">
          <tr>{head.map((h) => <th key={h} className="px-4 py-2.5 font-medium">{h}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
