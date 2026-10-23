-- Core schema for the Agentic RAG Assistant.
-- ${embedding_dimensions} is a Flyway placeholder (app property spring.flyway.placeholders.embedding_dimensions)
-- and must equal the output dimension of the configured embedding model.

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id                                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                              varchar(100) NOT NULL,
    email                             varchar(254) NOT NULL,
    password_hash                     varchar(100) NOT NULL,
    role                              varchar(20)  NOT NULL DEFAULT 'USER',
    enabled                           boolean      NOT NULL DEFAULT true,
    memory_enabled                    boolean      NOT NULL DEFAULT true,
    delete_memories_with_conversation boolean      NOT NULL DEFAULT false,
    default_mode                      varchar(20)  NOT NULL DEFAULT 'AUTO',
    created_at                        timestamptz  NOT NULL DEFAULT now(),
    updated_at                        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_mode CHECK (default_mode IN ('AUTO', 'DOCUMENTS', 'WEB', 'CHAT'))
);

CREATE TABLE password_reset_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_reset_tokens_user ON password_reset_tokens (user_id);

-- ---------------------------------------------------------------- conversations & messages
CREATE TABLE conversations (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title           varchar(200) NOT NULL,
    mode            varchar(20)  NOT NULL DEFAULT 'AUTO',
    archived        boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_conversations_mode CHECK (mode IN ('AUTO', 'DOCUMENTS', 'WEB', 'CHAT'))
);
CREATE INDEX ix_conversations_user_updated ON conversations (user_id, archived, updated_at DESC);

CREATE TABLE messages (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id    uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id            uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role               varchar(20) NOT NULL,
    content            text        NOT NULL,
    status             varchar(20) NOT NULL DEFAULT 'COMPLETED',
    mode               varchar(20),
    used_documents     boolean     NOT NULL DEFAULT false,
    used_web           boolean     NOT NULL DEFAULT false,
    used_memory        boolean     NOT NULL DEFAULT false,
    error_message      varchar(500),
    prompt_tokens      integer,
    completion_tokens  integer,
    created_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_messages_status CHECK (status IN ('COMPLETED', 'CANCELLED', 'FAILED'))
);
CREATE INDEX ix_messages_conversation_created ON messages (conversation_id, created_at);

CREATE TABLE conversation_summaries (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id        uuid        NOT NULL UNIQUE REFERENCES conversations (id) ON DELETE CASCADE,
    user_id                uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    summary                text        NOT NULL,
    covered_message_count  integer     NOT NULL,
    last_message_created_at timestamptz NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- documents & chunks
CREATE TABLE documents (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    filename       varchar(255) NOT NULL,
    content_type   varchar(100) NOT NULL,
    file_type      varchar(10)  NOT NULL,
    size_bytes     bigint       NOT NULL,
    content_hash   varchar(64)  NOT NULL,
    storage_path   varchar(500) NOT NULL,
    status         varchar(20)  NOT NULL DEFAULT 'UPLOADED',
    progress       integer      NOT NULL DEFAULT 0,
    error_message  varchar(1000),
    page_count     integer,
    chunk_count    integer      NOT NULL DEFAULT 0,
    char_count     integer      NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    ingested_at    timestamptz,
    CONSTRAINT uq_documents_user_hash UNIQUE (user_id, content_hash),
    CONSTRAINT chk_documents_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT chk_documents_type CHECK (file_type IN ('PDF', 'TXT', 'MD', 'DOCX')),
    CONSTRAINT chk_documents_progress CHECK (progress BETWEEN 0 AND 100)
);
CREATE INDEX ix_documents_user_created ON documents (user_id, created_at DESC);

CREATE TABLE document_chunks (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  uuid        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    chunk_index  integer     NOT NULL,
    page_number  integer,
    section      varchar(300),
    content      text        NOT NULL,
    char_count   integer     NOT NULL,
    embedding    vector(${embedding_dimensions}) NOT NULL,
    content_tsv  tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_chunks_document_index UNIQUE (document_id, chunk_index)
);
CREATE INDEX ix_chunks_user ON document_chunks (user_id);
CREATE INDEX ix_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ix_chunks_tsv ON document_chunks USING gin (content_tsv);

-- ---------------------------------------------------------------- persistent memory
CREATE TABLE memories (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content                 varchar(1000) NOT NULL,
    category                varchar(30)  NOT NULL,
    origin                  varchar(20)  NOT NULL,
    source_conversation_id  uuid REFERENCES conversations (id) ON DELETE SET NULL,
    source_message_id       uuid REFERENCES messages (id) ON DELETE SET NULL,
    confidence              real,
    expires_at              timestamptz,
    embedding               vector(${embedding_dimensions}),
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    last_used_at            timestamptz,
    CONSTRAINT chk_memories_category CHECK (category IN ('PREFERENCE', 'PROJECT', 'FACT', 'GOAL', 'INSTRUCTION')),
    CONSTRAINT chk_memories_origin CHECK (origin IN ('EXPLICIT', 'EXTRACTED', 'MANUAL')),
    CONSTRAINT chk_memories_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);
CREATE INDEX ix_memories_user_updated ON memories (user_id, updated_at DESC);
CREATE INDEX ix_memories_source_conversation ON memories (source_conversation_id);
CREATE INDEX ix_memories_embedding ON memories USING hnsw (embedding vector_cosine_ops);

-- ---------------------------------------------------------------- agent executions, sources, feedback
CREATE TABLE agent_executions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    conversation_id      uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    assistant_message_id uuid REFERENCES messages (id) ON DELETE SET NULL,
    mode                 varchar(20) NOT NULL,
    model                varchar(100),
    status               varchar(20) NOT NULL,
    iterations           integer     NOT NULL DEFAULT 0,
    tool_call_count      integer     NOT NULL DEFAULT 0,
    tool_calls           jsonb       NOT NULL DEFAULT '[]'::jsonb,
    error_message        varchar(1000),
    started_at           timestamptz NOT NULL,
    finished_at          timestamptz,
    duration_ms          bigint,
    CONSTRAINT chk_exec_status CHECK (status IN ('COMPLETED', 'CANCELLED', 'FAILED', 'TIMED_OUT'))
);
CREATE INDEX ix_exec_user_started ON agent_executions (user_id, started_at DESC);
CREATE INDEX ix_exec_started ON agent_executions (started_at DESC);

CREATE TABLE message_sources (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id    uuid         NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    citation      integer      NOT NULL,
    source_type   varchar(20)  NOT NULL,
    title         varchar(500) NOT NULL,
    url           varchar(2048),
    document_id   uuid REFERENCES documents (id) ON DELETE SET NULL,
    chunk_index   integer,
    page_number   integer,
    snippet       text,
    score         real,
    cited         boolean      NOT NULL DEFAULT false,
    retrieved_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_sources_type CHECK (source_type IN ('DOCUMENT', 'WEB', 'MEMORY'))
);
CREATE INDEX ix_sources_message ON message_sources (message_id);

CREATE TABLE response_feedback (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id  uuid        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating      varchar(10) NOT NULL,
    comment     varchar(1000),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_message_user UNIQUE (message_id, user_id),
    CONSTRAINT chk_feedback_rating CHECK (rating IN ('UP', 'DOWN'))
);

-- ---------------------------------------------------------------- audit log
CREATE TABLE audit_logs (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id     uuid REFERENCES users (id) ON DELETE SET NULL,
    actor_email  varchar(254),
    action       varchar(60)  NOT NULL,
    target_type  varchar(40),
    target_id    varchar(64),
    details      varchar(1000),
    ip_address   varchar(64),
    created_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);
