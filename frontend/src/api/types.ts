// Mirrors the backend DTOs (see backend/src/main/java/.../*Dtos.java).

export type Mode = 'AUTO' | 'DOCUMENTS' | 'WEB' | 'CHAT';
export type Role = 'USER' | 'ADMIN';

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  memoryEnabled: boolean;
  deleteMemoriesWithConversation: boolean;
  defaultMode: Mode;
  createdAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ConversationItem {
  id: string;
  title: string;
  mode: Mode;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationDetail extends ConversationItem {
  messages: Message[];
}

export type SourceType = 'DOCUMENT' | 'WEB' | 'MEMORY';

export interface Source {
  citation: number;
  type: SourceType;
  title: string;
  url?: string;
  documentId?: string;
  chunkIndex?: number;
  pageNumber?: number;
  snippet?: string;
  score?: number;
  cited: boolean;
  retrievedAt: string;
}

export type Rating = 'UP' | 'DOWN';

export interface Message {
  id: string;
  conversationId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  status: 'COMPLETED' | 'CANCELLED' | 'FAILED';
  mode?: Mode;
  usedDocuments: boolean;
  usedWeb: boolean;
  usedMemory: boolean;
  errorMessage?: string;
  createdAt: string;
  sources?: Source[];
  feedback?: Rating;
}

export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED';

export interface DocumentItem {
  id: string;
  filename: string;
  fileType: 'PDF' | 'TXT' | 'MD' | 'DOCX';
  sizeBytes: number;
  status: DocumentStatus;
  progress: number;
  errorMessage?: string;
  pageCount?: number;
  chunkCount: number;
  charCount: number;
  createdAt: string;
  updatedAt: string;
  ingestedAt?: string;
}

export type MemoryCategory = 'PREFERENCE' | 'PROJECT' | 'FACT' | 'GOAL' | 'INSTRUCTION';

export interface MemoryItem {
  id: string;
  content: string;
  category: MemoryCategory;
  origin: 'EXPLICIT' | 'EXTRACTED' | 'MANUAL';
  sourceConversationId?: string;
  confidence?: number;
  createdAt: string;
  updatedAt: string;
  lastUsedAt?: string;
}

export interface MemorySettings {
  enabled: boolean;
  deleteWithConversation: boolean;
}

export interface SystemStatus {
  chatAvailable: boolean;
  chatModel?: string;
  embeddingAvailable: boolean;
  webSearchAvailable: boolean;
  searchProvider: string;
  maxUploadMegabytes: number;
  version?: string;
}

export interface AdminStats {
  users: number;
  conversations: number;
  userMessages: number;
  assistantMessages: number;
  documentsReady: number;
  documentsProcessing: number;
  documentsFailed: number;
  executionsCompleted: number;
  executionsFailed: number;
  executionsTimedOut: number;
  executionsCancelled: number;
  executionsLast24h: number;
  averageDurationMsLast24h?: number;
  feedbackUp: number;
  feedbackDown: number;
  activeRuns: number;
}

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface ExecutionItem {
  id: string;
  userId: string;
  conversationId: string;
  mode: Mode;
  model?: string;
  status: 'COMPLETED' | 'CANCELLED' | 'FAILED' | 'TIMED_OUT';
  iterations: number;
  toolCallCount: number;
  errorMessage?: string;
  startedAt: string;
  durationMs?: number;
}

export interface AuditItem {
  id: string;
  actorId?: string;
  actorEmail?: string;
  action: string;
  targetType?: string;
  targetId?: string;
  details?: string;
  createdAt: string;
}

export interface ChatResponse {
  userMessage: Message;
  assistantMessage: Message;
}
