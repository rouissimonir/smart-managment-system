package com.smartmgmt.search;

import java.util.List;

/**
 * @param semanticMeaningful false when the offline hash embedder is active.
 *         Keyword hits are still real; semantic order is not.
 */
public record SearchResponse(
        String query,
        SearchMode mode,
        String embeddingModel,
        boolean semanticMeaningful,
        int page,
        int size,
        List<SearchHit> hits) {
}
