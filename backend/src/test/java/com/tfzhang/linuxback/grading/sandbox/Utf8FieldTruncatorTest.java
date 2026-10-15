package com.tfzhang.linuxback.grading.sandbox;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Utf8FieldTruncatorTest {

    @Test
    void truncatesEachFieldTo8192BytesAndNamesThem() {
        String huge = "a".repeat(9000);
        SandboxRunner.SandboxOutcome raw = new SandboxRunner.SandboxOutcome(
                huge, huge, huge, 0, false);
        Utf8FieldTruncator.Truncation truncated = Utf8FieldTruncator.truncate(raw);
        assertEquals(Utf8FieldTruncator.MAX_BYTES,
                truncated.outcome().stdout().getBytes(StandardCharsets.UTF_8).length);
        assertEquals(Utf8FieldTruncator.MAX_BYTES,
                truncated.outcome().stderr().getBytes(StandardCharsets.UTF_8).length);
        assertEquals(Utf8FieldTruncator.MAX_BYTES,
                truncated.outcome().compileError().getBytes(StandardCharsets.UTF_8).length);
        assertEquals(java.util.List.of("compileError", "stdout", "stderr"), truncated.truncatedFields());
    }

    @Test
    void cutsAtUtf8CharBoundary() {
        String value = "a".repeat(Utf8FieldTruncator.MAX_BYTES - 1) + "€";
        String truncated = Utf8FieldTruncator.truncateUtf8(value);
        byte[] bytes = truncated.getBytes(StandardCharsets.UTF_8);
        assertTrue(bytes.length <= Utf8FieldTruncator.MAX_BYTES);
        assertEquals("a".repeat(Utf8FieldTruncator.MAX_BYTES - 1), truncated);
    }

    @Test
    void leavesShortFieldsAlone() {
        SandboxRunner.SandboxOutcome raw = new SandboxRunner.SandboxOutcome(
                null, "ok\n", "", 0, true);
        Utf8FieldTruncator.Truncation truncated = Utf8FieldTruncator.truncate(raw);
        assertTrue(truncated.truncatedFields().isEmpty());
        assertEquals("ok\n", truncated.outcome().stdout());
        assertNullSafeCompileError(truncated);
    }

    private static void assertNullSafeCompileError(Utf8FieldTruncator.Truncation truncated) {
        assertEquals(null, truncated.outcome().compileError());
    }
}
