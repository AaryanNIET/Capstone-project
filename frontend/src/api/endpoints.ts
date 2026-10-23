import { api } from './client';
import type {
  AdminStats, AdminUser, AuditItem, ChatResponse, ConversationDetail, ConversationItem, DocumentItem, ExecutionItem,
  MemoryCategory, MemoryItem, MemorySettings, Mode, Page, Rating, Role, Source, SystemStatus, User,
} from './types';

export const auth = {
  me: () => api<User>('/api/auth/me'),
  login: (email: string, password: string) => api<User>('/api/auth/login', { method: 'POST', body: { email, password } }),
  register: (name: string, email: string, password: string) =>
    api<User>('/api/auth/register', { method: 'POST', body: { name, email, password } }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST' }),
  requestReset: (email: string) => api<void>('/api/auth/password-reset/request', { method: 'POST', body: { email } }),
  confirmReset: (token: string, newPassword: string) =>
    api<void>('/api/auth/password-reset/confirm', { method: 'POST', body: { token, newPassword } }),
};

export const system = {
  status: () => api<SystemStatus>('/api/system/status'),
};

export const profile = {
  update: (body: { name?: string; defaultMode?: Mode }) => api<User>('/api/users/me', { method: 'PATCH', body }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/api/users/me/password', { method: 'POST', body: { currentPassword, newPassword } }),
  deleteAccount: (password: string) => api<void>('/api/users/me', { method: 'DELETE', body: { password } }),
};

export const conversations = {
  list: (q: string, archived: boolean) =>
    api<Page<ConversationItem>>(`/api/conversations?size=100&archived=${archived}${q ? `&q=${encodeURIComponent(q)}` : ''}`),
  get: (id: string) => api<ConversationDetail>(`/api/conversations/${id}`),
  create: (mode?: Mode) => api<ConversationItem>('/api/conversations', { method: 'POST', body: mode ? { mode } : {} }),
  update: (id: string, body: { title?: string; archived?: boolean; mode?: Mode }) =>
    api<ConversationItem>(`/api/conversations/${id}`, { method: 'PATCH', body }),
  remove: (id: string) => api<void>(`/api/conversations/${id}`, { method: 'DELETE' }),
};

export const chat = {
  send: (id: string, content: string, mode?: Mode) =>
    api<ChatResponse>(`/api/chat/${id}/messages`, { method: 'POST', body: { content, mode } }),
  cancel: (id: string) => api<{ cancelled: boolean }>(`/api/chat/${id}/cancel`, { method: 'POST' }),
};

export const messages = {
  sources: (id: string) => api<Source[]>(`/api/messages/${id}/sources`),
  rate: (id: string, rating: Rating, comment?: string) =>
    api<unknown>(`/api/messages/${id}/feedback`, { method: 'PUT', body: { rating, comment } }),
  unrate: (id: string) => api<void>(`/api/messages/${id}/feedback`, { method: 'DELETE' }),
};

export const documents = {
  list: () => api<Page<DocumentItem>>('/api/documents?size=200'),
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api<DocumentItem>('/api/documents', { method: 'POST', body: form });
  },
  reingest: (id: string) => api<DocumentItem>(`/api/documents/${id}/reingest`, { method: 'POST' }),
  remove: (id: string) => api<void>(`/api/documents/${id}`, { method: 'DELETE' }),
};

export const memories = {
  list: (category?: MemoryCategory) =>
    api<Page<MemoryItem>>(`/api/memories?size=200${category ? `&category=${category}` : ''}`),
  create: (content: string, category: MemoryCategory) =>
    api<MemoryItem>('/api/memories', { method: 'POST', body: { content, category } }),
  update: (id: string, body: { content?: string; category?: MemoryCategory }) =>
    api<MemoryItem>(`/api/memories/${id}`, { method: 'PATCH', body }),
  remove: (id: string) => api<void>(`/api/memories/${id}`, { method: 'DELETE' }),
  clear: () => api<{ deleted: number }>('/api/memories', { method: 'DELETE' }),
  settings: () => api<MemorySettings>('/api/memory/settings'),
  updateSettings: (body: Partial<MemorySettings>) =>
    api<MemorySettings>('/api/memory/settings', { method: 'PATCH', body }),
};

export const admin = {
  stats: () => api<AdminStats>('/api/admin/stats'),
  users: () => api<Page<AdminUser>>('/api/admin/users?size=200'),
  updateUser: (id: string, body: { enabled?: boolean; role?: Role }) =>
    api<AdminUser>(`/api/admin/users/${id}`, { method: 'PATCH', body }),
  executions: () => api<Page<ExecutionItem>>('/api/admin/executions?size=100'),
  auditLogs: () => api<Page<AuditItem>>('/api/admin/audit-logs?size=100'),
};
