package com.smartmgmt.chat;

import java.util.UUID;

import com.smartmgmt.search.RetrievedChunk;

public record ChatSource(UUID documentId, String name, String locator, UUID chunkId) {

    public static ChatSource from(RetrievedChunk chunk) {
        return new ChatSource(chunk.documentId(), chunk.documentName(), chunk.locator(), chunk.chunkId());
    }
}
