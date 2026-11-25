package com.smartmgmt.search;

import java.util.UUID;

public record SearchHit(
        UUID chunkId,
        UUID documentId,
        String documentName,
        UUID projectId,
        String locator,
        String snippet,
        double score) {
}
