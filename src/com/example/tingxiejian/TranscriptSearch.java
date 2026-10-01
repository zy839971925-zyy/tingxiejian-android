package com.example.tingxiejian;

import java.util.regex.Pattern;

/** Literal search of the selected transcript layer; never modifies the stored transcript. */
final class TranscriptSearch {
    static Pattern pattern(String query) {
        String text = query == null ? "" : query.trim();
        return text.isEmpty() ? null : Pattern.compile(Pattern.quote(text),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
    static boolean matches(String text, Pattern pattern) {
        return pattern == null || text != null && pattern.matcher(text).find();
    }
}
