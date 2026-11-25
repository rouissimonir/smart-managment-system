package com.smartmgmt.chat;

import java.util.List;

import com.smartmgmt.search.RetrievedChunk;

/** Prompt text and the rule that an "I don't know" answer carries no citations. */
public final class Grounding {

    public static final String UNKNOWN = "I don't know.";

    private Grounding() {
    }

    public static String systemPrompt() {
        return """
                You are the assistant for this management system.
                Answer only using the excerpts in the user message.
                If the excerpts do not contain the answer, reply with exactly: I don't know.
                Do not use outside knowledge. Mention the document name when you use an excerpt.
                Do not invent document names, pages, or chunk ids.""";
    }

    public static String userPrompt(String question, List<RetrievedChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Excerpts:\n");
        for (RetrievedChunk chunk : chunks) {
            prompt.append("[").append(chunk.chunkId()).append("] ")
                    .append(chunk.documentName());
            if (chunk.locator() != null && !chunk.locator().isBlank()) {
                prompt.append(" (").append(chunk.locator()).append(")");
            }
            prompt.append("\n").append(chunk.content()).append("\n\n");
        }
        prompt.append("Question:\n").append(question);
        return prompt.toString();
    }

    public static List<ChatSource> sourcesFor(String answer, List<RetrievedChunk> chunks) {
        if (answer == null || isUnknown(answer) || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream().map(ChatSource::from).toList();
    }

    public static boolean isUnknown(String answer) {
        String trimmed = answer.trim();
        return trimmed.equalsIgnoreCase("I don't know.") || trimmed.equalsIgnoreCase("I don't know");
    }

    public static String extractive(RetrievedChunk top) {
        String where = top.documentName();
        if (top.locator() != null && !top.locator().isBlank()) {
            where = where + ", " + top.locator();
        }
        return "No language model is configured, so this is the closest passage from " + where + ":\n\n"
                + top.content();
    }
}
