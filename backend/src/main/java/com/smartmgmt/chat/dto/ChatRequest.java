package com.smartmgmt.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        UUID conversationId,
        UUID projectId,
        @NotBlank @Size(max = 4000) String message) {
}
