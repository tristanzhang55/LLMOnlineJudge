package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ScoreJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScoreJsonParser() {
    }

    static ParsedScore parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty model output");
        }
        String json = extractJsonObject(raw.trim());
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.has("score") || !node.get("score").isNumber()) {
                throw new IllegalArgumentException("score missing");
            }
            int score = node.get("score").asInt();
            String evidence = node.has("evidence") && !node.get("evidence").isNull()
                    ? node.get("evidence").asText("")
                    : "";
            return new ParsedScore(score, evidence);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("unparseable score JSON", e);
        }
    }

    static String extractJsonObject(String raw) {
        String stripped = raw;
        if (stripped.startsWith("```")) {
            int firstNl = stripped.indexOf('\n');
            stripped = firstNl >= 0 ? stripped.substring(firstNl + 1) : stripped.substring(3);
            int fence = stripped.lastIndexOf("```");
            if (fence >= 0) {
                stripped = stripped.substring(0, fence);
            }
            stripped = stripped.trim();
        }
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("no JSON object in model output");
        }
        return stripped.substring(start, end + 1);
    }

    record ParsedScore(int score, String evidence) {
    }
}
