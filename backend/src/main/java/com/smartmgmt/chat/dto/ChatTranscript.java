package com.smartmgmt.chat.dto;

import java.util.List;
import java.util.UUID;

public record ChatTranscript(
        UUID conversationId,
        UUID projectId,
        String title,
        List<ChatMessageView> messages) {
}
