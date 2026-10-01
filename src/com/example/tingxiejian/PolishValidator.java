package com.example.tingxiejian;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed structural and conservative text validation; this is not a semantic fact checker.
 * Content characters keep their exact order. Only standalone fillers and punctuation at an
 * existing boundary (or a segment edge) may change. Arbitrary names therefore need no guessed NER.
 */
final class PolishValidator {
    static final int MAX_SEGMENT_CHARS = 4096;
    static final int MAX_SEGMENTS = 32;
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;
    private static final String PUNCTUATION = ",，。.!！?？;；:：、";
    private static final Pattern FILLER = Pattern.compile(
            "(^|[\\s，,。.!！?？;；:：、])(?:嗯+|呃+|唔+)(?=$|[\\s，,。.!！?？;；:：、])");
    private static final Pattern NUMBERS = Pattern.compile(
            "[+−-]?[0-9０-９]+(?:[.,，．:/：/-][0-9０-９]+)*(?:[%％]|元|万元|亿元|美元|人民币)?");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z][A-Za-z0-9_’'\\-]*(?:\\.[A-Za-z][A-Za-z0-9_’'\\-]*)*");

    static final class Segment {
        final String id;
        final String original;
        Segment(String id, String original) { this.id = id; this.original = original; }
    }

    static final class ValidationException extends Exception {
        ValidationException(String message) { super(message); }
    }

    static void validateInput(List<Segment> segments) throws ValidationException {
        if (segments == null || segments.isEmpty() || segments.size() > MAX_SEGMENTS)
            throw invalid("整理段落数量异常");
        Set<String> ids = new HashSet<>();
        for (Segment segment : segments) {
            if (segment == null || segment.id == null || !segment.id.matches("[A-Za-z0-9_-]{1,128}")
                    || !ids.add(segment.id)) throw invalid("整理段落标识无效或重复");
            if (segment.original == null || segment.original.trim().isEmpty()
                    || segment.original.length() > MAX_SEGMENT_CHARS)
                throw invalid("整理段落长度异常");
            checkCharacters(segment.original);
        }
    }

    static Map<String, String> validate(String response, List<Segment> segments,
                                       List<String> hotwords) throws ValidationException {
        validateInput(segments);
        validateHotwords(hotwords);
        if (response == null || response.length() > MAX_RESPONSE_CHARS)
            throw invalid("整理响应长度异常");
        // Android's JSONObject accepts several non-JSON extensions. Validate strict grammar first.
        new StrictJson(response).validate();
        Map<String, Segment> expected = new LinkedHashMap<>();
        for (Segment segment : segments) expected.put(segment.id, segment);
        Map<String, String> accepted = new LinkedHashMap<>();
        try {
            JSONObject root = new JSONObject(response);
            if (root.length() != 1 || !root.has("edits")) throw invalid("整理响应结构异常");
            JSONArray edits = root.optJSONArray("edits");
            if (edits == null || edits.length() != segments.size()) throw invalid("整理响应缺少或重复段落");
            for (int i = 0; i < edits.length(); i++) {
                JSONObject edit = edits.optJSONObject(i);
                if (edit == null || edit.length() < 3 || edit.length() > 4
                        || !edit.has("segment_id") || !edit.has("original") || !edit.has("polished")
                        || (edit.length() == 4 && !edit.has("reason"))) throw invalid("整理编辑结构异常");
                String id = string(edit, "segment_id");
                Segment segment = expected.get(id);
                if (segment == null || accepted.containsKey(id)) throw invalid("整理响应段落标识未知或重复");
                String original = string(edit, "original");
                String polished = string(edit, "polished");
                if (!segment.original.equals(original)) throw invalid("整理响应原文不匹配");
                if (edit.has("reason") && string(edit, "reason").length() > 512)
                    throw invalid("整理说明过长");
                validateText(original, polished, hotwords);
                accepted.put(id, polished);
            }
        } catch (ValidationException error) {
            throw error;
        } catch (Exception error) {
            throw invalid("整理响应不是有效的结构化 JSON");
        }
        return accepted;
    }

    static void validateHotwords(List<String> hotwords) throws ValidationException {
        if (hotwords == null) return;
        if (hotwords.size() > 256) throw invalid("整理热词数量过多");
        for (String word : hotwords) {
            if (word == null) continue;
            if (word.length() > 128) throw invalid("整理热词过长");
            checkCharacters(word);
        }
    }

    private static String string(JSONObject value, String key) throws Exception {
        Object field = value.get(key);
        if (!(field instanceof String)) throw invalid("整理响应字段类型异常");
        return (String) field;
    }

    private static void validateText(String original, String polished, List<String> hotwords)
            throws ValidationException {
        if (polished.trim().isEmpty() || polished.length() > MAX_SEGMENT_CHARS
                || polished.length() > original.length() * 1.2 + 24)
            throw invalid("整理文字为空或改动过大");
        checkCharacters(polished);
        if (!matches(NUMBERS, original).equals(matches(NUMBERS, polished)))
            throw invalid("整理改变了数字、日期、金额或百分比");
        if (!matches(LATIN, original).equals(matches(LATIN, polished)))
            throw invalid("整理改变了专名");
        if (hotwords != null) {
            for (String hotword : hotwords) {
                if (hotword != null && !hotword.isEmpty()
                        && occurrences(original, hotword) != occurrences(polished, hotword))
                    throw invalid("整理改变了用户热词");
            }
        }
        String source = removeFillers(original);
        String target = removeFillers(polished);
        String sourceContent = content(source);
        if (!sourceContent.equals(content(target))) throw invalid("整理改变了原文内容或专名");
        // Moving a comma across a negation can reverse meaning despite identical characters.
        // Preserve every internal boundary, and only permit additional punctuation at segment edges.
        if (!boundaries(source, sourceContent.length()).equals(boundaries(target, sourceContent.length())))
            throw invalid("整理移动了原文语句边界");
    }

    private static String removeFillers(String text) {
        String previous;
        do { previous = text; text = FILLER.matcher(text).replaceAll("$1"); }
        while (!previous.equals(text));
        return text;
    }

    private static String content(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && PUNCTUATION.indexOf(c) < 0) result.append(c);
        }
        return result.toString();
    }

    private static Set<Integer> boundaries(String text, int contentLength) {
        Set<Integer> result = new HashSet<>();
        int position = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (PUNCTUATION.indexOf(c) >= 0) {
                if (position > 0 && position < contentLength) result.add(position);
            } else if (!Character.isWhitespace(c)) position++;
        }
        return result;
    }

    private static List<String> matches(Pattern pattern, String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static int occurrences(String text, String word) {
        int result = 0;
        for (int position = 0; (position = text.indexOf(word, position)) >= 0; position++) result++;
        return result;
    }

    private static void checkCharacters(String text) throws ValidationException {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
                    || Character.getType(c) == Character.FORMAT)
                throw invalid("整理文字含不允许的控制字符");
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                    throw invalid("整理文字 Unicode 异常");
            } else if (Character.isLowSurrogate(c)) throw invalid("整理文字 Unicode 异常");
        }
    }

    private static ValidationException invalid(String message) { return new ValidationException(message); }

    /** Strict JSON grammar, with depth/duplicate-key checks before the platform parser is used. */
    private static final class StrictJson {
        private final String text;
        private int position;
        StrictJson(String text) { this.text = text; }
        void validate() throws ValidationException {
            whitespace();
            value(0);
            whitespace();
            if (position != text.length()) fail();
        }
        private void value(int depth) throws ValidationException {
            if (depth > 16 || position >= text.length()) fail();
            char c = text.charAt(position);
            if (c == '{') object(depth + 1);
            else if (c == '[') array(depth + 1);
            else if (c == '"') string();
            else if (c == 't') literal("true");
            else if (c == 'f') literal("false");
            else if (c == 'n') literal("null");
            else number();
        }
        private void object(int depth) throws ValidationException {
            position++; whitespace();
            Set<String> keys = new HashSet<>();
            if (take('}')) return;
            do {
                whitespace();
                if (!keys.add(string())) fail();
                whitespace(); require(':'); whitespace(); value(depth); whitespace();
                if (take('}')) return;
                require(',');
            } while (true);
        }
        private void array(int depth) throws ValidationException {
            position++; whitespace();
            if (take(']')) return;
            do {
                whitespace(); value(depth); whitespace();
                if (take(']')) return;
                require(',');
            } while (true);
        }
        private String string() throws ValidationException {
            require('"');
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') { checkCharacters(result.toString()); return result.toString(); }
                if (c < 0x20) fail();
                if (c == '\\') {
                    if (position >= text.length()) fail();
                    char escaped = text.charAt(position++);
                    if (escaped == 'u') {
                        if (position + 4 > text.length()) fail();
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            int digit = Character.digit(text.charAt(position++), 16);
                            if (digit < 0) fail();
                            code = code * 16 + digit;
                        }
                        c = (char) code;
                    } else {
                        int index = "\"\\/bfnrt".indexOf(escaped);
                        if (index < 0) fail();
                        c = "\"\\/\b\f\n\r\t".charAt(index);
                    }
                }
                result.append(c);
            }
            fail(); return "";
        }
        private void number() throws ValidationException {
            take('-');
            if (!take('0')) {
                if (position >= text.length() || text.charAt(position) < '1' || text.charAt(position) > '9') fail();
                digits();
            }
            if (take('.')) { int start = position; digits(); if (start == position) fail(); }
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                int start = position; digits(); if (start == position) fail();
            }
        }
        private void digits() {
            while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') position++;
        }
        private void literal(String literal) throws ValidationException {
            if (!text.startsWith(literal, position)) fail();
            position += literal.length();
        }
        private void whitespace() {
            while (position < text.length() && " \r\n\t".indexOf(text.charAt(position)) >= 0) position++;
        }
        private boolean take(char c) {
            if (position < text.length() && text.charAt(position) == c) { position++; return true; }
            return false;
        }
        private void require(char c) throws ValidationException { if (!take(c)) fail(); }
        private void fail() throws ValidationException { throw invalid("整理响应不是严格 JSON"); }
    }
}
