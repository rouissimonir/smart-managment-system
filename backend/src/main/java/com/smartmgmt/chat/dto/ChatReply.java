package com.smartmgmt.chat.dto;

import java.util.List;
import java.util.UUID;

import com.smartmgmt.chat.ChatSource;

public record ChatReply(
        UUID conversationId,
        String answer,
        List<ChatSource> sources,
        String model) {
}
