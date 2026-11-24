package com.smartmgmt.chat;

/**
 * One completion call. {@link #generative()} is false for the offline client,
 * which lets the assistant return a cited passage without pretending it reasoned.
 */
public interface ChatClient {

    boolean generative();

    String modelId();

    String complete(String systemPrompt, String userPrompt);
}
