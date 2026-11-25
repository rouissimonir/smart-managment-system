package com.smartmgmt.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.smartmgmt.search.RetrievedChunk;

class GroundingTest {

    @Test
    void unknownAnswerDropsCitations() {
        RetrievedChunk chunk = chunk();
        assertThat(Grounding.sourcesFor("I don't know.", List.of(chunk))).isEmpty();
        assertThat(Grounding.sourcesFor("I don't know", List.of(chunk))).isEmpty();
    }

    @Test
    void groundedAnswerCitesEveryRetrievedChunk() {
        RetrievedChunk chunk = chunk();
        List<ChatSource> sources = Grounding.sourcesFor("Customers can request a refund within 30 days.", List.of(chunk));
        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.documentId()).isEqualTo(chunk.documentId());
            assertThat(source.chunkId()).isEqualTo(chunk.chunkId());
            assertThat(source.name()).isEqualTo("Refund Policy.md");
            assertThat(source.locator()).isEqualTo("Page 3");
        });
    }

    @Test
    void promptNamesChunkIdsAndForbidsOutsideKnowledge() {
        String system = Grounding.systemPrompt();
        String user = Grounding.userPrompt("What is the refund window?", List.of(chunk()));
        assertThat(system).contains("I don't know.");
        assertThat(user).contains("Question:").contains("Refund Policy.md").contains("within 30 days");
    }

    private static RetrievedChunk chunk() {
        return new RetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Refund Policy.md",
                null,
                "Customers can request a refund within 30 days.",
                "Page 3",
                0.8);
    }
}
