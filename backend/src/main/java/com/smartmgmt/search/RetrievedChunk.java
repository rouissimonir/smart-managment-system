package com.smartmgmt.search;

import java.util.UUID;

/** A retrieved chunk with its full text, for a prompt rather than a search snippet. */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String documentName,
        UUID projectId,
        String content,
        String locator,
        double score) {
}
