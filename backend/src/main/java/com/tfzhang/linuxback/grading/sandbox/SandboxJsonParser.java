package com.tfzhang.linuxback.grading.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class SandboxJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "compileError", "stdout", "stderr", "exitCode", "timedOut");

    private SandboxJsonParser() {
    }

    static SandboxRunner.SandboxOutcome parseLastLine(byte[] stdoutBytes) {
        String stdout = new String(stdoutBytes == null ? new byte[0] : stdoutBytes, StandardCharsets.UTF_8);
        String line = lastNonEmptyLine(stdout);
        if (line == null) {
            throw new IllegalStateException("sandbox stdout had no JSON line");
        }
        return parseObject(line);
    }

    static String lastNonEmptyLine(String stdout) {
        if (stdout == null || stdout.isEmpty()) {
            return null;
        }
        String[] lines = stdout.split("\n", -1);
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isEmpty()) {
                String line = lines[i];
                return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            }
        }
        return null;
    }

    static SandboxRunner.SandboxOutcome parseObject(String jsonLine) {
        JsonNode node;
        try {
            node = MAPPER.readTree(jsonLine);
        } catch (Exception e) {
            throw new IllegalStateException("sandbox last-line JSON was malformed", e);
        }
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("sandbox last-line JSON was not an object");
        }
        Set<String> keys = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            keys.add(names.next());
        }
        if (!keys.equals(REQUIRED_KEYS)) {
            throw new IllegalStateException("sandbox last-line JSON must have exactly the five contract keys");
        }
        JsonNode compileErrorNode = node.get("compileError");
        String compileError;
        if (compileErrorNode.isNull()) {
            compileError = null;
        } else if (compileErrorNode.isTextual()) {
            compileError = compileErrorNode.asText();
            if (compileError.isEmpty()) {
                throw new IllegalStateException("sandbox compileError must be null, not empty string");
            }
        } else {
            throw new IllegalStateException("sandbox compileError must be string or null");
        }
        if (!node.get("stdout").isTextual()) {
            throw new IllegalStateException("sandbox stdout must be a string");
        }
        if (!node.get("stderr").isTextual()) {
            throw new IllegalStateException("sandbox stderr must be a string");
        }
        JsonNode exitNode = node.get("exitCode");
        Integer exitCode;
        if (exitNode.isNull()) {
            exitCode = null;
        } else if (exitNode.isInt() || exitNode.isIntegralNumber()) {
            exitCode = exitNode.intValue();
        } else {
            throw new IllegalStateException("sandbox exitCode must be int or null");
        }
        if (!node.get("timedOut").isBoolean()) {
            throw new IllegalStateException("sandbox timedOut must be a boolean");
        }
        return new SandboxRunner.SandboxOutcome(
                compileError,
                node.get("stdout").asText(),
                node.get("stderr").asText(),
                exitCode,
                node.get("timedOut").asBoolean());
    }
}
