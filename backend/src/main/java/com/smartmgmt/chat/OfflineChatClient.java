package com.smartmgmt.chat;

/** Placeholder so the bean exists. The service does not call it when {@link #generative()} is false. */
public class OfflineChatClient implements ChatClient {

    @Override
    public boolean generative() {
        return false;
    }

    @Override
    public String modelId() {
        return "offline";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        throw new UnsupportedOperationException("Offline chat does not call a model");
    }
}
