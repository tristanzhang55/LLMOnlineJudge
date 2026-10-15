package com.tfzhang.linuxback.grading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreJsonParserTest {

    @Test
    void parse_plainObject() {
        ScoreJsonParser.ParsedScore parsed = ScoreJsonParser.parse("{\"score\": 3, \"evidence\": \"ok\"}");
        assertEquals(3, parsed.score());
        assertEquals("ok", parsed.evidence());
    }

    @Test
    void parse_fencedMarkdown() {
        ScoreJsonParser.ParsedScore parsed = ScoreJsonParser.parse("""
                ```json
                {"score": 1, "evidence": "partial"}
                ```
                """);
        assertEquals(1, parsed.score());
        assertEquals("partial", parsed.evidence());
    }

    @Test
    void parse_rejectsMissingScore() {
        assertThrows(IllegalArgumentException.class, () -> ScoreJsonParser.parse("{\"evidence\":\"x\"}"));
    }
}
