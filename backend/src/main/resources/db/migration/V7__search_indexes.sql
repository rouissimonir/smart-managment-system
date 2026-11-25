-- Phase 7: keyword (FTS) + semantic (HNSW) indexes.
-- The vector extension already exists (V1). content is NOT NULL, so the
-- generated tsvector is always defined.

ALTER TABLE document_chunks
    ADD COLUMN content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_document_chunks_content_tsv ON document_chunks USING GIN (content_tsv);

-- Document names are short; an expression index lets a query token match the title
-- even when the chunk body uses different words.
CREATE INDEX idx_documents_name_tsv ON documents USING GIN (to_tsvector('english', name));

-- Cosine distance matches the <=> operator used by the search query.
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
