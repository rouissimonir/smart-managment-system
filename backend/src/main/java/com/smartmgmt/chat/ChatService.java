package com.smartmgmt.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartmgmt.auth.SecurityUtils;
import com.smartmgmt.auth.UserPrincipal;
import com.smartmgmt.chat.dto.ChatMessageView;
import com.smartmgmt.chat.dto.ChatReply;
import com.smartmgmt.chat.dto.ChatRequest;
import com.smartmgmt.chat.dto.ChatTranscript;
import com.smartmgmt.common.NotFoundException;
import com.smartmgmt.management.document.ingest.EmbeddingClient;
import com.smartmgmt.management.project.Project;
import com.smartmgmt.management.project.ProjectRepository;
import com.smartmgmt.management.user.Role;
import com.smartmgmt.management.user.UserRepository;
import com.smartmgmt.search.RetrievedChunk;
import com.smartmgmt.search.SearchRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ChatService {

    private static final TypeReference<List<ChatSource>> SOURCES = new TypeReference<>() {
    };

    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final SearchRepository search;
    private final EmbeddingClient embeddings;
    private final ChatClient chatClient;
    private final ChatProperties properties;
    private final ObjectMapper objectMapper;
    private final IntentRouter router;
    private final DataTools dataTools;

    public ChatService(ChatConversationRepository conversations, ChatMessageRepository messages,
            UserRepository users, ProjectRepository projects, SearchRepository search,
            EmbeddingClient embeddings, ChatClient chatClient, ChatProperties properties,
            ObjectMapper objectMapper, IntentRouter router, DataTools dataTools) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.projects = projects;
        this.search = search;
        this.embeddings = embeddings;
        this.chatClient = chatClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.router = router;
        this.dataTools = dataTools;
    }

    public ChatReply ask(ChatRequest request) {
        UserPrincipal current = SecurityUtils.currentUser();
        String question = request.message().trim();
        Intent intent = router.route(question);
        ChatConversation conversation = resolveConversation(request, current);
        Project project = conversation.getProject();
        UUID projectId = project == null ? null : project.getId();

        String answer = Grounding.UNKNOWN;
        List<ChatSource> sources = List.of();
        String model = chatClient.modelId();
        switch (intent) {
            case GENERAL -> {
                answer = """
                        I can look up projects, tasks, and customers you are allowed to see, \
                        and answer from uploaded documents with citations. \
                        Try "Who owns Project Alpha?" or "What is the refund policy?\"""";
                sources = List.of();
                model = "help";
            }
            case SQL_DATA -> {
                answer = dataTools.answer(question, current);
                sources = List.of();
                model = "tools";
            }
            case HYBRID -> {
                String toolText = dataTools.answer(question, current);
                List<RetrievedChunk> chunks = retrieve(question, projectId, current);
                if (chunks.isEmpty()) {
                    answer = toolText;
                    sources = List.of();
                } else if (chatClient.generative()) {
                    String prompt = "Application data:\n" + toolText + "\n\n" + Grounding.userPrompt(question, chunks);
                    answer = chatClient.complete(Grounding.systemPrompt(), prompt).trim();
                    sources = Grounding.sourcesFor(answer, chunks);
                    if (Grounding.isUnknown(answer)) {
                        answer = toolText;
                        sources = List.of();
                    }
                } else {
                    answer = toolText + "\n\n" + Grounding.extractive(chunks.get(0));
                    sources = chunks.stream().map(ChatSource::from).toList();
                }
                model = chatClient.generative() ? chatClient.modelId() : "tools";
            }
            case VECTOR_RAG -> {
                List<RetrievedChunk> chunks = retrieve(question, projectId, current);
                if (chunks.isEmpty()) {
                    answer = Grounding.UNKNOWN;
                    sources = List.of();
                } else if (chatClient.generative()) {
                    answer = chatClient.complete(Grounding.systemPrompt(), Grounding.userPrompt(question, chunks)).trim();
                    sources = Grounding.sourcesFor(answer, chunks);
                } else {
                    answer = Grounding.extractive(chunks.get(0));
                    sources = chunks.stream().map(ChatSource::from).toList();
                }
                model = chatClient.modelId();
            }
        }

        save(conversation, ChatRole.USER, question, null);
        save(conversation, ChatRole.ASSISTANT, answer, sources);
        return new ChatReply(conversation.getId(), answer, sources, model);
    }

    private List<RetrievedChunk> retrieve(String question, UUID projectId, UserPrincipal current) {
        if (properties.getTopK() <= 0) {
            return List.of();
        }
        float[] vector = embeddings.embed(List.of(question)).get(0);
        return search.nearest(vector, projectId, current.getId(), current.getRole() != Role.USER, properties.getTopK());
    }

    @Transactional(readOnly = true)
    public ChatTranscript transcript(UUID conversationId) {
        ChatConversation conversation = loadOwned(conversationId, SecurityUtils.currentUser());
        List<ChatMessageView> views = messages.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(message -> new ChatMessageView(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        readSources(message.getSources()),
                        message.getCreatedAt()))
                .toList();
        Project project = conversation.getProject();
        return new ChatTranscript(
                conversation.getId(),
                project == null ? null : project.getId(),
                conversation.getTitle(),
                views);
    }

    private ChatConversation resolveConversation(ChatRequest request, UserPrincipal current) {
        if (request.conversationId() != null) {
            ChatConversation existing = loadOwned(request.conversationId(), current);
            UUID existingProject = existing.getProject() == null ? null : existing.getProject().getId();
            if (request.projectId() != null && !request.projectId().equals(existingProject)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "projectId does not match this conversation");
            }
            return existing;
        }
        Project project = null;
        if (request.projectId() != null) {
            project = projects.findById(request.projectId())
                    .orElseThrow(() -> new NotFoundException("Project", request.projectId()));
            requireVisible(project, current);
        }
        ChatConversation created = new ChatConversation();
        created.setUser(users.findById(current.getId())
                .orElseThrow(() -> new NotFoundException("User", current.getId())));
        created.setProject(project);
        created.setTitle(title(request.message()));
        return conversations.save(created);
    }

    private ChatConversation loadOwned(UUID id, UserPrincipal current) {
        ChatConversation conversation = conversations.findById(id)
                .orElseThrow(() -> new NotFoundException("Conversation", id));
        if (conversation.getUser() == null || !conversation.getUser().getId().equals(current.getId())) {
            throw new AccessDeniedException("You do not have access to this conversation");
        }
        return conversation;
    }

    private void requireVisible(Project project, UserPrincipal current) {
        if (current.getRole() == Role.USER
                && (project.getOwner() == null || !project.getOwner().getId().equals(current.getId()))) {
            throw new AccessDeniedException("You do not have access to this project");
        }
    }

    private void save(ChatConversation conversation, ChatRole role, String content, List<ChatSource> sources) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setSources(sources == null || sources.isEmpty() ? null : writeSources(sources));
        messages.save(message);
    }

    private String writeSources(List<ChatSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not store citations", e);
        }
    }

    private List<ChatSource> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SOURCES);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private static String title(String message) {
        String trimmed = message.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 197) + "...";
    }
}
