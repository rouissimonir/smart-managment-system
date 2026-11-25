package com.smartmgmt.chat;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    /** How many chunks are placed in the prompt. */
    private int topK = 5;

    private final OpenAi openai = new OpenAi();

    @Getter
    @Setter
    public static class OpenAi {

        /** Blank means the offline extractive answer. Same env var as embeddings. */
        private String apiKey = "";

        private String model = "gpt-4o-mini";

        private String baseUrl = "https://api.openai.com/v1";

        private Duration timeout = Duration.ofSeconds(60);
    }
}
