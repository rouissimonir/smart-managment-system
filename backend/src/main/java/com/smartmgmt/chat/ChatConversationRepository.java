package com.smartmgmt.chat;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {
}
