package com.smartmgmt.search;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reciprocal rank fusion. Rank is 1-based. An item in both lists beats an item
 * that only one list ranked highly, which is the point of hybrid search.
 */
public final class ReciprocalRankFusion {

    /** Standard constant from Cormack, Clarke, Buettcher. */
    public static final int K = 60;

    private ReciprocalRankFusion() {
    }

    public static List<SearchHit> fuse(List<SearchHit> keyword, List<SearchHit> semantic, int limit) {
        Map<UUID, Accumulator> byChunk = new LinkedHashMap<>();
        add(byChunk, keyword);
        add(byChunk, semantic);
        return byChunk.values().stream()
                .sorted(Comparator.comparingDouble(Accumulator::score).reversed())
                .limit(Math.max(limit, 0))
                .map(Accumulator::hit)
                .toList();
    }

    private static void add(Map<UUID, Accumulator> byChunk, List<SearchHit> ranked) {
        for (int i = 0; i < ranked.size(); i++) {
            SearchHit hit = ranked.get(i);
            double contribution = 1.0 / (K + i + 1);
            byChunk.compute(hit.chunkId(), (id, existing) -> {
                if (existing == null) {
                    return new Accumulator(hit, contribution);
                }
                existing.score += contribution;
                return existing;
            });
        }
    }

    private static final class Accumulator {
        private final SearchHit hit;
        private double score;

        private Accumulator(SearchHit hit, double score) {
            this.hit = hit;
            this.score = score;
        }

        private double score() {
            return score;
        }

        private SearchHit hit() {
            return new SearchHit(
                    hit.chunkId(),
                    hit.documentId(),
                    hit.documentName(),
                    hit.projectId(),
                    hit.locator(),
                    hit.snippet(),
                    score);
        }
    }
}
