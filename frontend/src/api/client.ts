/**
 * Thin fetch wrapper. Authentication is the HttpOnly session cookie; state-changing requests echo the readable
 * XSRF-TOKEN cookie in the X-XSRF-TOKEN header. The backend rotates that token on login, so it is read per request,
 * and a request rejected with CSRF_INVALID is retried once with a fresh token.
 */

export const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

export function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

async function ensureCsrfCookie(force = false): Promise<string | null> {
  if (force || !readCookie('XSRF-TOKEN')) {
    await fetch(API_BASE + '/api/auth/csrf', { credentials: 'include' });
  }
  return readCookie('XSRF-TOKEN');
}

export async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = await res.json();
    return new ApiError(res.status, body.code ?? 'ERROR', body.message ?? res.statusText, body.fieldErrors ?? {});
  } catch {
    return new ApiError(res.status, 'ERROR', res.status >= 500 ? 'The server is unavailable. Please try again.' : res.statusText);
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

/** Sends a request and returns the raw response; throws ApiError for non-2xx statuses. */
export async function rawRequest(path: string, opts: RequestOptions = {}, retried = false): Promise<Response> {
  const method = opts.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers };
  let body: BodyInit | undefined;
  if (opts.body instanceof FormData) {
    body = opts.body;
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  if (method !== 'GET') {
    const token = await ensureCsrfCookie(retried);
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  let res: Response;
  try {
    res = await fetch(API_BASE + path, { method, headers, body, credentials: 'include', signal: opts.signal });
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e;
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection.');
  }
  if (res.ok) return res;
  const error = await toApiError(res);
  if (error.code === 'CSRF_INVALID' && !retried) {
    return rawRequest(path, opts, true);
  }
  if (error.status === 401 && !path.startsWith('/api/auth/')) {
    window.dispatchEvent(new Event('auth:expired'));
  }
  throw error;
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await rawRequest(path, opts);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    const fields = Object.values(e.fieldErrors);
    return fields.length ? fields.join('. ') : e.message;
  }
  return e instanceof Error ? e.message : 'Something went wrong';
}
