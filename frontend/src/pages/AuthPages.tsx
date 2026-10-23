import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';

import { ApiError, errorMessage } from '../api/client';
import { auth } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Spinner } from '../components/Spinner';
import { Alert } from '../components/ui';

const PASSWORD_HINT = 'At least 8 characters, with a letter and a digit.';
const PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,128}$/;

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle?: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="flex min-h-full items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center text-center">
          <img src="/favicon.svg" alt="" className="mb-3 h-11 w-11" />
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {subtitle && <p className="muted mt-1 text-sm">{subtitle}</p>}
        </div>
        <div className="card">{children}</div>
        {footer && <p className="muted mt-4 text-center text-sm">{footer}</p>}
      </div>
    </div>
  );
}

function useSubmit<T>(action: () => Promise<T>, onSuccess: (r: T) => void) {
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      onSuccess(await action());
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setFieldErrors(err.fieldErrors);
      else setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return { submit, error, fieldErrors, busy };
}

function Field({ label, error, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  const id = props.id ?? props.name;
  return (
    <div>
      <label htmlFor={id} className="label">{label}</label>
      <input id={id} className="input" aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} {...props} />
      {error && <p id={`${id}-error`} className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}

export function LoginPage() {
  const { setUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const from = (location.state as { from?: string } | null)?.from ?? '/chat';
  const { submit, error, busy } = useSubmit(() => auth.login(email, password), (user) => {
    setUser(user);
    navigate(from, { replace: true });
  });

  return (
    <AuthShell title="Sign in" subtitle="Welcome back to Agentic RAG Assistant"
               footer={<>No account? <Link className="font-medium text-indigo-600 hover:underline" to="/register">Create one</Link></>}>
      <form className="space-y-4" onSubmit={submit}>
        {error && <Alert>{error}</Alert>}
        <Field label="Email" name="email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field label="Password" name="password" type="password" autoComplete="current-password" required value={password}
               onChange={(e) => setPassword(e.target.value)} />
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner />}Sign in</button>
        <p className="text-center text-sm"><Link className="text-indigo-600 hover:underline" to="/forgot-password">Forgot your password?</Link></p>
      </form>
    </AuthShell>
  );
}

export function RegisterPage() {
  const { setUser } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);
  const { submit, error, fieldErrors, busy } = useSubmit(() => auth.register(name, email, password), (user) => {
    setUser(user);
    navigate('/chat', { replace: true });
  });

  return (
    <AuthShell title="Create your account" subtitle="Chat with your documents, the web and a memory that learns"
               footer={<>Already registered? <Link className="font-medium text-indigo-600 hover:underline" to="/login">Sign in</Link></>}>
      <form className="space-y-4" onSubmit={(e) => {
        if (!PASSWORD_RE.test(password)) {
          e.preventDefault();
          setLocalError(PASSWORD_HINT);
          return;
        }
        setLocalError(null);
        void submit(e);
      }}>
        {error && <Alert>{error}</Alert>}
        <Field label="Name" name="name" autoComplete="name" required maxLength={100} value={name}
               onChange={(e) => setName(e.target.value)} error={fieldErrors.name} />
        <Field label="Email" name="email" type="email" autoComplete="email" required value={email}
               onChange={(e) => setEmail(e.target.value)} error={fieldErrors.email} />
        <Field label="Password" name="password" type="password" autoComplete="new-password" required value={password}
               onChange={(e) => setPassword(e.target.value)} error={localError ?? fieldErrors.password} />
        {!localError && !fieldErrors.password && <p className="muted -mt-2 text-xs">{PASSWORD_HINT}</p>}
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner />}Create account</button>
      </form>
    </AuthShell>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const { submit, error, busy } = useSubmit(() => auth.requestReset(email), () => setSent(true));
  return (
    <AuthShell title="Reset your password"
               footer={<Link className="text-indigo-600 hover:underline" to="/login">Back to sign in</Link>}>
      {sent ? (
        <Alert kind="success">If an account exists for {email}, we sent a link to reset the password. It expires in 30 minutes.</Alert>
      ) : (
        <form className="space-y-4" onSubmit={submit}>
          {error && <Alert>{error}</Alert>}
          <Field label="Email" name="email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner />}Send reset link</button>
        </form>
      )}
    </AuthShell>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);
  const { submit, error, busy } = useSubmit(() => auth.confirmReset(token, password),
    () => navigate('/login', { replace: true, state: { reset: true } }));

  if (!token) {
    return (
      <AuthShell title="Reset your password">
        <Alert>This reset link is incomplete. Request a new one from the sign-in page.</Alert>
      </AuthShell>
    );
  }
  return (
    <AuthShell title="Choose a new password">
      <form className="space-y-4" onSubmit={(e) => {
        const problem = !PASSWORD_RE.test(password) ? PASSWORD_HINT : password !== confirm ? 'Passwords do not match.' : null;
        if (problem) {
          e.preventDefault();
          setLocalError(problem);
          return;
        }
        void submit(e);
      }}>
        {(localError || error) && <Alert>{localError ?? error}</Alert>}
        <Field label="New password" name="password" type="password" autoComplete="new-password" required value={password}
               onChange={(e) => setPassword(e.target.value)} />
        <Field label="Confirm password" name="confirm" type="password" autoComplete="new-password" required value={confirm}
               onChange={(e) => setConfirm(e.target.value)} />
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner />}Set password</button>
      </form>
    </AuthShell>
  );
}
