package com.smartmgmt.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
