# End-to-end walkthrough

A browser test of the whole stack that needs no API key. `mock-openai.mjs` is a small OpenAI-compatible server that
streams like the real API (fragmented tool calls, a usage chunk), so the backend's real Spring AI OpenAI client and
agent loop are exercised. `walkthrough.mjs` drives an installed Chrome through register, upload, a cited
documents-mode answer, feedback, regenerate, memory, stop, search, settings, admin, and phone-width dark mode.
It writes screenshots to `shots/`.

```bash
cd e2e && npm install
npm run mock                                    # terminal 1: mock model on :11500

# terminal 2: database (fresh, the walkthrough registers admin@example.com)
docker run -d --rm --name ara-e2e-db -e POSTGRES_DB=agentic_rag -e POSTGRES_USER=agentic \
  -e POSTGRES_PASSWORD=agentic -p 5432:5432 pgvector/pgvector:0.8.1-pg17

# terminal 3: backend pointed at the mock
cd backend && AI_PROVIDER=openai AI_BASE_URL=http://localhost:11500 AI_API_KEY=mock AI_MODEL=mock-gpt \
  EMBEDDING_PROVIDER=hash EMBEDDING_DIMENSIONS=384 ADMIN_EMAILS=admin@example.com mvn spring-boot:run

cd frontend && npm run dev                      # terminal 4
cd e2e && npm run walkthrough                   # prints PASS/FAIL per check; exit code 1 on failure
```

Set `BROWSER_CHANNEL=msedge` to use Edge, or `BASE_URL` to test another deployment
(for example `http://localhost:3000` for docker compose, with the backend configured as above).
