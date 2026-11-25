package com.smartmgmt.chat;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * A single chat completion. Temperature is 0 so citations stay close to the excerpts.
 */
public class OpenAiChatClient implements ChatClient {

    private final RestClient client;
    private final ChatProperties properties;

    public OpenAiChatClient(RestClient.Builder builder, ChatProperties properties) {
        this.properties = properties;
        this.client = builder
                .baseUrl(properties.getOpenai().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getOpenai().getApiKey())
                .build();
    }

    @Override
    public boolean generative() {
        return true;
    }

    @Override
    public String modelId() {
        return properties.getOpenai().getModel();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", properties.getOpenai().getModel(),
                "temperature", 0,
                "max_tokens", 500,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        ChatCompletion response = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(ChatCompletion.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null) {
            throw new IllegalStateException("Chat completion returned no message");
        }
        return response.choices().get(0).message().content();
    }

    record ChatCompletion(List<Choice> choices) {
        record Choice(Message message) {
        }

        record Message(String content) {
        }
    }
}
