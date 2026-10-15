package com.tfzhang.linuxback.grading.sandbox;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxJsonParserTest {

    @Test
    void parseLastNonEmptyLine() {
        String stdout = "noise\n{\"compileError\":null,\"stdout\":\"3\\n\",\"stderr\":\"\",\"exitCode\":0,\"timedOut\":false}\n";
        SandboxRunner.SandboxOutcome outcome = SandboxJsonParser.parseLastLine(
                stdout.getBytes(StandardCharsets.UTF_8));
        assertNull(outcome.compileError());
        assertEquals("3\n", outcome.stdout());
        assertEquals("", outcome.stderr());
        assertEquals(0, outcome.exitCode());
        assertEquals(false, outcome.timedOut());
    }

    @Test
    void missingJsonThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SandboxJsonParser.parseLastLine("not json\n".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ex.getMessage().contains("malformed") || ex.getMessage().contains("keys"));
    }

    @Test
    void extraKeyThrows() {
        String json = "{\"compileError\":null,\"stdout\":\"\",\"stderr\":\"\",\"exitCode\":0,\"timedOut\":false,\"gcc\":\"w\"}";
        assertThrows(IllegalStateException.class, () -> SandboxJsonParser.parseObject(json));
    }
}
