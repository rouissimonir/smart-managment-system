package com.smartmgmt.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ChatConfig {

    @Bean
    public ChatClient chatClient(ChatProperties properties, RestClient.Builder builder) {
        String apiKey = properties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new OfflineChatClient();
        }
        return new OpenAiChatClient(builder, properties);
    }
}
