import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { Brain, FileText, LogOut, Menu, MessageSquare, Settings, Shield, X } from 'lucide-react';

import { useAuth } from '../auth/AuthContext';

const NAV = [
  { to: '/chat', label: 'Chat', icon: MessageSquare },
  { to: '/documents', label: 'Documents', icon: FileText },
  { to: '/memory', label: 'Memory', icon: Brain },
  { to: '/settings', label: 'Settings', icon: Settings },
];

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const items = user?.role === 'ADMIN' ? [...NAV, { to: '/admin', label: 'Admin', icon: Shield }] : NAV;

  const links = (
    <>
      {items.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          onClick={() => setOpen(false)}
          className={({ isActive }) => clsx('btn justify-start',
            isActive ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300'
              : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800')}
        >
          <Icon className="h-4 w-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </>
  );

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center gap-4 border-b border-slate-200 bg-white px-4 dark:border-slate-800 dark:bg-slate-900">
        <button className="btn-ghost p-2 md:hidden" aria-label="Open menu" onClick={() => setOpen((v) => !v)}>
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
        <NavLink to="/chat" className="flex items-center gap-2 font-semibold">
          <img src="/favicon.svg" alt="" className="h-7 w-7" />
          <span className="hidden sm:inline">Agentic RAG Assistant</span>
        </NavLink>
        <nav className="ml-4 hidden items-center gap-1 md:flex" aria-label="Main">{links}</nav>
        <div className="ml-auto flex items-center gap-2">
          <span className="muted hidden text-sm sm:inline" title={user?.email}>{user?.name}</span>
          <button
            className="btn-ghost p-2"
            title="Sign out"
            aria-label="Sign out"
            onClick={async () => {
              await logout();
              navigate('/login');
            }}
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </header>
      {open && (
        <nav className="flex flex-col gap-1 border-b border-slate-200 bg-white p-2 md:hidden dark:border-slate-800 dark:bg-slate-900" aria-label="Main">
          {links}
        </nav>
      )}
      <main className="min-h-0 flex-1">
        <Outlet />
      </main>
    </div>
  );
}

/** Scrollable page container for the non-chat screens. */
export function PageBody({ children }: { children: React.ReactNode }) {
  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-5xl px-4 py-8">{children}</div>
    </div>
  );
}
