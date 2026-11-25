package com.smartmgmt.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.smartmgmt.chat.ChatRole;
import com.smartmgmt.chat.ChatSource;

public record ChatMessageView(
        UUID id,
        ChatRole role,
        String content,
        List<ChatSource> sources,
        Instant createdAt) {
}
