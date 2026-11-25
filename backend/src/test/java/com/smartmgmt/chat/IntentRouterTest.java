package com.smartmgmt.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntentRouterTest {

    private final IntentRouter router = new IntentRouter();

    @Test
    void ownershipQuestionStaysOnTheDataPath() {
        assertThat(router.route("Who owns Project Alpha?")).isEqualTo(Intent.SQL_DATA);
    }

    @Test
    void policyQuestionStaysOnRetrieval() {
        assertThat(router.route("What is the refund policy?")).isEqualTo(Intent.VECTOR_RAG);
    }

    @Test
    void helpDoesNotTouchDataOrDocuments() {
        assertThat(router.route("What can you do?")).isEqualTo(Intent.GENERAL);
    }

    @Test
    void comparisonUsesBoth() {
        assertThat(router.route("Compare the refund policy with Project Alpha")).isEqualTo(Intent.HYBRID);
    }

    @Test
    void projectNameDropsTheLeadingWordAndPunctuation() {
        assertThat(DataTools.extractName("Who owns Project Alpha?")).isEqualTo("Alpha");
    }
}
