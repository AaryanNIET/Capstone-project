import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';

import { errorMessage } from '../api/client';
import { profile, system } from '../api/endpoints';
import type { Mode } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageBody } from '../components/Layout';
import { Spinner } from '../components/Spinner';
import { Alert, PageHeader } from '../components/ui';
import { MODES } from '../lib/format';

export function SettingsPage() {
  const { user, setUser } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState(user?.name ?? '');
  const [defaultMode, setDefaultMode] = useState<Mode>(user?.defaultMode ?? 'AUTO');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [deletePassword, setDeletePassword] = useState('');
  const status = useQuery({ queryKey: ['system-status'], queryFn: system.status, staleTime: 60_000 });

  const saveProfile = useMutation({
    mutationFn: () => profile.update({ name: name.trim(), defaultMode }),
    onSuccess: setUser,
  });
  const changePassword = useMutation({
    mutationFn: () => profile.changePassword(currentPassword, newPassword),
    onSuccess: () => { setCurrentPassword(''); setNewPassword(''); },
  });
  const deleteAccount = useMutation({
    mutationFn: () => profile.deleteAccount(deletePassword),
    onSuccess: () => { setUser(null); navigate('/login', { replace: true }); },
  });

  return (
    <PageBody>
      <PageHeader title="Settings" description={user?.email} />
      <div className="grid gap-6 lg:grid-cols-2">
        <form className="card space-y-4" onSubmit={(e) => { e.preventDefault(); saveProfile.mutate(); }}>
          <h2 className="font-medium">Profile</h2>
          <div>
            <label className="label" htmlFor="name">Display name</label>
            <input id="name" className="input" value={name} maxLength={100} required onChange={(e) => setName(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="mode">Default mode for new chats</label>
            <select id="mode" className="input" value={defaultMode} onChange={(e) => setDefaultMode(e.target.value as Mode)}>
              {MODES.map((m) => <option key={m.value} value={m.value}>{m.label}: {m.hint}</option>)}
            </select>
          </div>
          {saveProfile.isError && <Alert>{errorMessage(saveProfile.error)}</Alert>}
          {saveProfile.isSuccess && <Alert kind="success">Profile saved.</Alert>}
          <button className="btn-primary" disabled={saveProfile.isPending || !name.trim()}>
            {saveProfile.isPending && <Spinner />}Save profile
          </button>
        </form>

        <form className="card space-y-4" onSubmit={(e) => { e.preventDefault(); changePassword.mutate(); }}>
          <h2 className="font-medium">Change password</h2>
          <div>
            <label className="label" htmlFor="current">Current password</label>
            <input id="current" type="password" className="input" autoComplete="current-password" required
                   value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="new">New password</label>
            <input id="new" type="password" className="input" autoComplete="new-password" required minLength={8}
                   value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
            <p className="muted mt-1 text-xs">At least 8 characters, with a letter and a digit.</p>
          </div>
          {changePassword.isError && <Alert>{errorMessage(changePassword.error)}</Alert>}
          {changePassword.isSuccess && <Alert kind="success">Password changed.</Alert>}
          <button className="btn-primary" disabled={changePassword.isPending}>
            {changePassword.isPending && <Spinner />}Change password
          </button>
        </form>

        <div className="card space-y-2">
          <h2 className="font-medium">Server capabilities</h2>
          {status.data ? (
            <ul className="space-y-1 text-sm">
              <li>AI model: {status.data.chatAvailable ? status.data.chatModel : <span className="text-amber-600">not configured</span>}</li>
              <li>Document search: {status.data.embeddingAvailable ? 'available' : <span className="text-amber-600">not configured</span>}</li>
              <li>Live web search: {status.data.webSearchAvailable ? `available (${status.data.searchProvider})` : <span className="text-amber-600">not configured</span>}</li>
            </ul>
          ) : <Spinner />}
        </div>

        <form className="card space-y-4 border-red-200 dark:border-red-900" onSubmit={(e) => { e.preventDefault(); deleteAccount.mutate(); }}>
          <h2 className="font-medium text-red-700 dark:text-red-400">Delete account</h2>
          <p className="muted text-sm">
            Permanently deletes your account, conversations, documents and memories. This cannot be undone.
          </p>
          <div>
            <label className="label" htmlFor="delete-password">Confirm with your password</label>
            <input id="delete-password" type="password" className="input" autoComplete="current-password" required
                   value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} />
          </div>
          {deleteAccount.isError && <Alert>{errorMessage(deleteAccount.error)}</Alert>}
          <button className="btn-danger" disabled={deleteAccount.isPending || !deletePassword}>
            {deleteAccount.isPending && <Spinner />}Delete my account
          </button>
        </form>
      </div>
    </PageBody>
  );
}
