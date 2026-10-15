package com.tfzhang.linuxback.grading.sandbox;

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Courtesy cap for LLM / gradingLog after the full JSON line is already in
 * memory. The receive bound is the container {@code /tmp} tmpfs (not 8 KiB).
 */
final class Utf8FieldTruncator {

    static final int MAX_BYTES = 8192;

    private Utf8FieldTruncator() {
    }

    static Truncation truncate(SandboxRunner.SandboxOutcome raw) {
        List<String> cut = new ArrayList<>();
        String compileError = truncateField(raw.compileError(), "compileError", cut);
        String stdout = truncateField(raw.stdout(), "stdout", cut);
        String stderr = truncateField(raw.stderr(), "stderr", cut);
        SandboxRunner.SandboxOutcome outcome = new SandboxRunner.SandboxOutcome(
                compileError, stdout, stderr, raw.exitCode(), raw.timedOut());
        return new Truncation(outcome, List.copyOf(cut));
    }

    static String truncateUtf8(String value) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BYTES) {
            return value;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, 0, MAX_BYTES)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, 0, MAX_BYTES, StandardCharsets.UTF_8);
        }
    }

    private static String truncateField(String value, String name, List<String> cut) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BYTES) {
            return value;
        }
        cut.add(name);
        return truncateUtf8(value);
    }

    record Truncation(SandboxRunner.SandboxOutcome outcome, List<String> truncatedFields) {
    }
}
