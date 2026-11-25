package com.smartmgmt.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SearchRankingTest {

    @Test
    void fusionPrefersAChunkFoundByBothLegs() {
        UUID both = UUID.randomUUID();
        UUID keywordOnly = UUID.randomUUID();
        UUID semanticOnly = UUID.randomUUID();

        List<SearchHit> fused = ReciprocalRankFusion.fuse(
                List.of(hit(both), hit(keywordOnly)),
                List.of(hit(both), hit(semanticOnly)),
                10);

        assertThat(fused).extracting(SearchHit::chunkId).containsExactly(both, keywordOnly, semanticOnly);
        assertThat(fused.get(0).score()).isGreaterThan(fused.get(1).score());
    }

    @Test
    void fusionScoreIsTheSumOfReciprocalRanks() {
        UUID id = UUID.randomUUID();
        List<SearchHit> fused = ReciprocalRankFusion.fuse(List.of(hit(id)), List.of(hit(id)), 5);
        double expected = 1.0 / (ReciprocalRankFusion.K + 1) + 1.0 / (ReciprocalRankFusion.K + 1);
        assertThat(fused.get(0).score()).isEqualTo(expected);
    }

    @Test
    void snippetCentersOnTheQueryTerm() {
        String content = "x".repeat(200) + " subscription termination applies after the trial " + "y".repeat(200);
        String snippet = Snippets.of(content, "customer cancellation termination");
        assertThat(snippet).contains("subscription termination");
        assertThat(snippet.length()).isLessThan(content.length());
    }

    @Test
    void vectorLiteralUsesADotDecimalAndBrackets() {
        String literal = SearchRepository.toVectorLiteral(new float[] {0.5f, -1.25f});
        assertThat(literal).isEqualTo("[0.500000,-1.250000]");
    }

    private static SearchHit hit(UUID id) {
        return new SearchHit(id, UUID.randomUUID(), "Doc", null, "p. 1", "snippet", 0.5);
    }
}
