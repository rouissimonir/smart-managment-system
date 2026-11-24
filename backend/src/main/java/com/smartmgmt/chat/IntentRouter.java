package com.smartmgmt.chat;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Deterministic router. A model call would be harder to test and could send a
 * data question into the vector index. Rules are ordered so "who owns" never
 * becomes retrieval, and a comparison of a policy and a project uses both.
 */
@Component
public class IntentRouter {

    public Intent route(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean policy = contains(text, "policy", "refund", "document", "according to", "procedure",
                "handbook", "guideline");
        boolean data = contains(text, "who owns", "how many", "overdue", "tasks for", "task for",
                "status of", "not updated", "not contacted", "which project");
        boolean compare = contains(text, "compare", "versus", " vs ", "vs.", "difference between");
        if ((policy && data) || (compare && (policy || data))) {
            return Intent.HYBRID;
        }
        if (data) {
            return Intent.SQL_DATA;
        }
        if (policy) {
            return Intent.VECTOR_RAG;
        }
        if (isGeneral(text)) {
            return Intent.GENERAL;
        }
        return Intent.VECTOR_RAG;
    }

    private static boolean isGeneral(String text) {
        String trimmed = text.trim();
        return trimmed.equals("help")
                || trimmed.equals("hello")
                || trimmed.equals("hi")
                || trimmed.startsWith("what can you")
                || trimmed.startsWith("how do i")
                || trimmed.startsWith("who are you");
    }

    private static boolean contains(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
