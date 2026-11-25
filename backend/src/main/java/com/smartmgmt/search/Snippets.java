package com.smartmgmt.search;

import java.util.Locale;

/** A short window of chunk text, biased toward the first query term it contains. */
public final class Snippets {

    static final int WINDOW = 280;

    private Snippets() {
    }

    public static String of(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= WINDOW) {
            return trimmed;
        }
        int at = firstTerm(trimmed.toLowerCase(Locale.ROOT), query);
        if (at < 0) {
            return trimmed.substring(0, WINDOW) + "…";
        }
        int start = Math.max(0, at - 80);
        int end = Math.min(trimmed.length(), start + WINDOW);
        String slice = trimmed.substring(start, end);
        if (start > 0) {
            slice = "…" + slice;
        }
        if (end < trimmed.length()) {
            slice = slice + "…";
        }
        return slice;
    }

    private static int firstTerm(String haystack, String query) {
        if (query == null) {
            return -1;
        }
        for (String term : query.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (term.length() < 3) {
                continue;
            }
            int at = haystack.indexOf(term);
            if (at >= 0) {
                return at;
            }
        }
        return -1;
    }
}
