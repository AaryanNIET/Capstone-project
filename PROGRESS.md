# Build Progress — Agentic RAG AI Assistant

Last updated 2026-10-23. Everything is built and verified, including a browser walkthrough against a mock OpenAI server. The only thing left is a run with a real model, which needs the user's API key.

## Environment
- JDK 25 is installed at `C:\Program Files\Java\jdk-25` and compiles with `--release 21`. JDK 17 is the default on PATH, so set `JAVA_HOME` to JDK 25 before running Maven.
- Pinned versions: Spring Boot 3.5.16, Spring AI 1.1.8 (built against Boot 3.5.16), springdoc 2.8.17, PDFBox 3.0.8, POI 5.5.1, Testcontainers 1.21.4 (version managed by Boot).
- Docker Desktop works. The `pgvector/pgvector:0.8.1-pg17` image has been pulled.

## Done (backend only, under `backend/`)
- `pom.xml`, `application.yml`, `AppProperties`
- Flyway migrations: `V1__core_schema.sql` (all tables, pgvector, HNSW and GIN indexes) and `V2__spring_session.sql`
- common, exception: `ApiError`, `PageResponse`, `Texts`, `ApiException`, `GlobalExceptionHandler`
- security: session-cookie auth, SPA CSRF, BCrypt, rate limiting, `SessionTerminator`
- auth and user: register, login, logout, me, CSRF, password reset over SMTP, profile, change password, delete account, admin bootstrap, audit log
- chat (persistence only): `Conversation`, `Message`, `ConversationSummary` and their repositories, `ConversationService` and `ConversationController`, `ChatDtos`
- source and feedback entities and repositories
- AI layer: `AiModels`, `DefaultAiModels` (OpenAI-compatible, Ollama, offline `hash` embedding), `EmbeddingService`
- documents and RAG: upload validation, storage, PDF/DOCX/MD/TXT extraction, `TextChunker`, async ingestion, `DocumentChunkStore` (pgvector and full-text search), `RetrievalService` (hybrid), `DocumentController`
- memory: entity, policy, rule-based candidate extractor, `MemoryService` (dedupe and contradiction replacement), `MemoryVectorStore`, `MemoryController`
- search: Tavily, Brave and SearXNG providers, `LiveSearchService` (cache, retry, dedupe), `UrlSafety` (SSRF checks), `WebPageFetcher`
- agent and tools: `AgentRunContext`, `AgentResult`, `RunSource`, `ToolCallRecord`, `AgentListener`, `AgentRequest`, `SystemPromptBuilder`, `AgentOrchestrator` (bounded streaming tool loop, citation sanitizing), `AgentExecution` and its repository, `ToolNames`, `ToolServices`, `AgentToolset`, `ToolExecutor`

- chat runtime: `ActiveRunRegistry` (one run per conversation, owner-only cancel), `ChatTurnService` (prepare/complete transactions, regenerate replaces the last reply), `ConversationSummaryService` (async rolling summary), `ChatService` and `ChatController` (`/messages`, `/stream` SSE, `/cancel`)
- `MemoryExtractionService` (runs before the agent; explicit requests are always considered, auto-extraction follows `MEMORY_AUTO_EXTRACT`)
- `SourceController`, `FeedbackController`, `SystemController` (public `GET /api/system/status`), `AdminController` (stats, users, executions, audit log), `OpenApiConfig`

**Status (2026-10-23):** the backend compiles and `mvn test` passes 54 tests: unit tests plus `ChatFlowIntegrationTest` (Testcontainers pgvector, a scripted `FakeChatModel` and the hash embedding model). It covers auth/CSRF, chat, SSE, document RAG with citation sanitizing, memory, user isolation, feedback and admin.

Bugs found and fixed by the tests:
- `SecurityConfig` injected `CorsConfigurationSource` by type; two beans match, so the app could not start. It now uses `cors(Customizer.withDefaults())`.
- `AuditService` wrote in `REQUIRES_NEW` before the caller committed, so registering failed with an FK violation. Audit entries are now written after commit.
- `MemoryCandidateExtractor` capitalized values inserted into templates ("User prefers Concise answers").

SSE events: `meta`, `status`, `tool`, `content`, `reset`, `sources`, `done`, `error`. If the client disconnects, the run still completes and is stored.

## Frontend and deployment (done 2026-10-23)
- `frontend/`: React 18, TypeScript, Vite 5, Tailwind 3, TanStack Query 5, react-markdown. Pages: login, register, forgot and reset password, chat (conversation sidebar, mode picker, SSE streaming with tool progress, stop, regenerate, citation chips, sources, feedback), documents, memory, settings, admin.
- CSRF: the client reads the `XSRF-TOKEN` cookie for each request and retries once on `CSRF_INVALID`. The body of `/api/auth/csrf` holds a masked token that the header check rejects.
- `npm test` passes 7 tests, and `npm run typecheck` and `npm run build` are clean.
- Docker: `backend/Dockerfile`, `frontend/Dockerfile` + `nginx.conf` + `security-headers.conf`, `docker-compose.yml`, `.env.example`. `docker compose up --build` was verified offline (hash embeddings, no model): register, chat SSE, upload and memory all work through nginx, and the security headers are present.
- A further fix: the memory extractor now ignores leading greetings ("Hello, my name is Sam"). The backend now has 55 tests.

## End-to-end verification (2026-10-23)
- `e2e/mock-openai.mjs` streams like the OpenAI API, with fragmented tool calls and a usage chunk. The backend uses it through the real `OpenAiChatModel`.
- `e2e/walkthrough.mjs` (playwright-core with the installed Chrome) passes all 26 checks: auth, upload, a documents answer with a citation chip, removal of the invented [9] and of the preamble, feedback, regenerate, memory, stop, search, settings, admin, and phone width in dark mode. The only console message is the expected 401 from the signed-out session check.
- UI fixes from the visual review: the composer's double focus ring, uncited memories now collapse instead of being listed as sources, cleaner text for an empty cancelled reply, and the React Router v7 future flags.

## Next steps
1. With a real key: `cp .env.example .env`, set `AI_API_KEY`, run `docker compose up --build`, and watch how the real model uses the tools and citations (prompt tuning may be needed)
2. Optional: a search provider key (Tavily or Brave) to try Web mode for real

## Resume
```bash
cd backend
export JAVA_HOME="/c/Program Files/Java/jdk-25"   # PowerShell: $env:JAVA_HOME="C:\Program Files\Java\jdk-25"
mvn test   # needs Docker running for the Testcontainers tests
# frontend: cd frontend && npm install && npm run dev   (proxies /api to :8080)
# full stack: cp .env.example .env && docker compose up --build   (http://localhost:3000)
```
