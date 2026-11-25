package com.smartmgmt.chat;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartmgmt.chat.dto.ChatReply;
import com.smartmgmt.chat.dto.ChatRequest;
import com.smartmgmt.chat.dto.ChatTranscript;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping
    public ChatReply ask(@Valid @RequestBody ChatRequest request) {
        return service.ask(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @GetMapping("/conversations/{id}")
    public ChatTranscript transcript(@PathVariable UUID id) {
        return service.transcript(id);
    }
}
