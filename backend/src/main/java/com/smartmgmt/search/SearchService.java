package com.smartmgmt.search;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartmgmt.auth.SecurityUtils;
import com.smartmgmt.auth.UserPrincipal;
import com.smartmgmt.common.NotFoundException;
import com.smartmgmt.management.document.ingest.EmbeddingClient;
import com.smartmgmt.management.project.Project;
import com.smartmgmt.management.project.ProjectRepository;
import com.smartmgmt.management.user.Role;

@Service
@Transactional(readOnly = true)
public class SearchService {

    static final int MAX_PAGE_SIZE = 20;
    /** How many rows each leg contributes before fusion. Hybrid pages inside this window. */
    static final int FUSION_POOL = 40;
    static final int MAX_QUERY_LENGTH = 500;

    private final SearchRepository search;
    private final EmbeddingClient embeddings;
    private final ProjectRepository projects;

    public SearchService(SearchRepository search, EmbeddingClient embeddings, ProjectRepository projects) {
        this.search = search;
        this.embeddings = embeddings;
        this.projects = projects;
    }

    public SearchResponse search(String query, SearchMode mode, UUID projectId, int page, int size) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query is required");
        }
        if (q.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query is too long");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        SearchMode effective = mode == null ? SearchMode.HYBRID : mode;

        UserPrincipal current = SecurityUtils.currentUser();
        boolean unrestricted = current.getRole() != Role.USER;
        requireProjectVisible(projectId, current);

        List<SearchHit> hits = switch (effective) {
            case KEYWORD -> search.keyword(q, projectId, current.getId(), unrestricted, pageSize, page * pageSize);
            case SEMANTIC -> semantic(q, projectId, current.getId(), unrestricted, pageSize, page * pageSize);
            case HYBRID -> hybrid(q, projectId, current.getId(), unrestricted, page, pageSize);
        };

        boolean meaningful = !embeddings.modelId().startsWith("offline-hash");
        return new SearchResponse(q, effective, embeddings.modelId(), meaningful, page, pageSize, hits);
    }

    private List<SearchHit> semantic(String query, UUID projectId, UUID userId, boolean unrestricted,
            int limit, int offset) {
        float[] vector = embeddings.embed(List.of(query)).get(0);
        return search.semantic(vector, query, projectId, userId, unrestricted, limit, offset);
    }

    private List<SearchHit> hybrid(String query, UUID projectId, UUID userId, boolean unrestricted,
            int page, int pageSize) {
        List<SearchHit> keyword = search.keyword(query, projectId, userId, unrestricted, FUSION_POOL, 0);
        float[] vector = embeddings.embed(List.of(query)).get(0);
        List<SearchHit> semantic = search.semantic(vector, query, projectId, userId, unrestricted, FUSION_POOL, 0);
        List<SearchHit> fused = ReciprocalRankFusion.fuse(keyword, semantic, FUSION_POOL);
        int from = page * pageSize;
        if (from >= fused.size()) {
            return List.of();
        }
        return fused.subList(from, Math.min(fused.size(), from + pageSize));
    }

    private void requireProjectVisible(UUID projectId, UserPrincipal current) {
        if (projectId == null) {
            return;
        }
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));
        if (current.getRole() == Role.USER
                && (project.getOwner() == null || !project.getOwner().getId().equals(current.getId()))) {
            throw new AccessDeniedException("You do not have access to this project");
        }
    }
}
