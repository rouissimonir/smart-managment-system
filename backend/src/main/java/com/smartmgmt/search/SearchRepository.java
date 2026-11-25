package com.smartmgmt.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Keyword and vector lookups. Native SQL because tsvector and {@code <=>} are
 * not expressible in JPQL. Visibility is applied in the query, not after the fact,
 * so a USER never receives another project's chunks even on page 1.
 */
@Repository
public class SearchRepository {

    private static final String VISIBILITY = """
            AND (CAST(:unrestricted AS boolean) OR p.owner_id = :userId)
            """;

    private static final String PROJECT_FILTER = """
            AND d.project_id = :projectId
            """;

    private static final String KEYWORD = """
            SELECT c.id, c.document_id, d.name, d.project_id, c.content, c.page_or_section,
                   ts_rank_cd(c.content_tsv, parsed.query)
                       + ts_rank_cd(to_tsvector('english', d.name), parsed.query) AS score
              FROM document_chunks c
              JOIN documents d ON d.id = c.document_id
              LEFT JOIN projects p ON p.id = d.project_id,
                   (SELECT COALESCE(NULLIF(replace(plainto_tsquery('english', :q)::text, ' & ', ' | '), ''), 'nomatch')::tsquery AS query) parsed
             WHERE (c.content_tsv @@ parsed.query OR to_tsvector('english', d.name) @@ parsed.query)
            """;

    private static final String SEMANTIC = """
            SELECT c.id, c.document_id, d.name, d.project_id, c.content, c.page_or_section,
                   1 - (c.embedding <=> CAST(:vec AS vector)) AS score
              FROM document_chunks c
              JOIN documents d ON d.id = c.document_id
              LEFT JOIN projects p ON p.id = d.project_id
             WHERE c.embedding IS NOT NULL
            """;

    private final EntityManager entityManager;

    public SearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<SearchHit> keyword(String query, UUID projectId, UUID userId, boolean unrestricted,
            int limit, int offset) {
        String sql = KEYWORD + visibility(projectId) + " ORDER BY score DESC, c.id LIMIT :limit OFFSET :offset";
        Query nativeQuery = entityManager.createNativeQuery(sql);
        nativeQuery.setParameter("q", query);
        bindScope(nativeQuery, projectId, userId, unrestricted);
        nativeQuery.setParameter("limit", limit);
        nativeQuery.setParameter("offset", offset);
        return map(nativeQuery, query);
    }

    public List<SearchHit> semantic(float[] vector, String queryText, UUID projectId, UUID userId,
            boolean unrestricted, int limit, int offset) {
        String sql = SEMANTIC + visibility(projectId)
                + " ORDER BY 7 DESC, c.id LIMIT :limit OFFSET :offset";
        Query nativeQuery = entityManager.createNativeQuery(sql);
        nativeQuery.setParameter("vec", toVectorLiteral(vector));
        bindScope(nativeQuery, projectId, userId, unrestricted);
        nativeQuery.setParameter("limit", limit);
        nativeQuery.setParameter("offset", offset);
        return map(nativeQuery, queryText);
    }

    /** Same visibility and ordering as {@link #semantic}, but keeps the full chunk text for a prompt. */
    public List<RetrievedChunk> nearest(float[] vector, UUID projectId, UUID userId, boolean unrestricted, int limit) {
        String sql = SEMANTIC + visibility(projectId)
                + " ORDER BY 7 DESC, c.id LIMIT :limit";
        Query nativeQuery = entityManager.createNativeQuery(sql);
        nativeQuery.setParameter("vec", toVectorLiteral(vector));
        bindScope(nativeQuery, projectId, userId, unrestricted);
        nativeQuery.setParameter("limit", limit);
        return mapChunks(nativeQuery);
    }

    private static String visibility(UUID projectId) {
        return (projectId == null ? "" : PROJECT_FILTER) + VISIBILITY;
    }

    private static void bindScope(Query query, UUID projectId, UUID userId, boolean unrestricted) {
        if (projectId != null) {
            query.setParameter("projectId", projectId);
        }
        query.setParameter("unrestricted", unrestricted);
        query.setParameter("userId", userId);
    }

    @SuppressWarnings("unchecked")
    private static List<SearchHit> map(Query query, String queryText) {
        List<Object[]> rows = query.getResultList();
        List<SearchHit> hits = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            hits.add(new SearchHit(
                    uuid(row[0]),
                    uuid(row[1]),
                    row[2] == null ? "" : row[2].toString(),
                    uuid(row[3]),
                    row[5] == null ? null : row[5].toString(),
                    Snippets.of(row[4] == null ? "" : row[4].toString(), queryText),
                    number(row[6])));
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private static List<RetrievedChunk> mapChunks(Query query) {
        List<Object[]> rows = query.getResultList();
        List<RetrievedChunk> chunks = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            chunks.add(new RetrievedChunk(
                    uuid(row[0]),
                    uuid(row[1]),
                    row[2] == null ? "" : row[2].toString(),
                    uuid(row[3]),
                    row[4] == null ? "" : row[4].toString(),
                    row[5] == null ? null : row[5].toString(),
                    number(row[6])));
        }
        return chunks;
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    private static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        return UUID.fromString(value.toString());
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }
}
