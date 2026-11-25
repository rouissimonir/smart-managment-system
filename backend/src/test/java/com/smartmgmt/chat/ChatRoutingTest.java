package com.smartmgmt.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmgmt.auth.UserPrincipal;
import com.smartmgmt.chat.dto.ChatRequest;
import com.smartmgmt.management.document.ingest.EmbeddingClient;
import com.smartmgmt.management.project.ProjectRepository;
import com.smartmgmt.management.user.Role;
import com.smartmgmt.management.user.User;
import com.smartmgmt.management.user.UserRepository;
import com.smartmgmt.search.SearchRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChatRoutingTest {

    @Mock ChatConversationRepository conversations;
    @Mock ChatMessageRepository messages;
    @Mock UserRepository users;
    @Mock ProjectRepository projects;
    @Mock SearchRepository search;
    @Mock EmbeddingClient embeddings;
    @Mock ChatClient chatClient;
    @Mock DataTools dataTools;
    @Mock ObjectMapper objectMapper;

    private ChatService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("manager@local");
        user.setRole(Role.MANAGER);
        user.setPasswordHash("x");
        user.setEnabled(true);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service = new ChatService(conversations, messages, users, projects, search, embeddings, chatClient,
                new ChatProperties(), objectMapper, new IntentRouter(), dataTools);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownershipQuestionUsesToolsAndDoesNotEmbed() {
        when(dataTools.answer(any(), any())).thenReturn("Project Alpha is IN_PROGRESS, owned by Manager User.");

        var reply = service.ask(new ChatRequest(null, null, "Who owns Project Alpha?"));

        assertThatAnswer(reply.answer(), "Manager User");
        verify(embeddings, never()).embed(any());
        verify(search, never()).nearest(any(), any(), any(), anyBoolean(), anyInt());
    }

    @Test
    void policyQuestionUsesRetrievalAndDoesNotCallTools() {
        when(chatClient.modelId()).thenReturn("offline");
        when(embeddings.embed(any())).thenReturn(List.of(new float[] {0.1f}));
        when(search.nearest(any(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of());

        var reply = service.ask(new ChatRequest(null, null, "What is the refund policy?"));

        assertThatAnswer(reply.answer(), "I don't know.");
        verify(dataTools, never()).answer(any(), any());
    }

    private static void assertThatAnswer(String answer, String expectedFragment) {
        org.assertj.core.api.Assertions.assertThat(answer).contains(expectedFragment);
    }
}
