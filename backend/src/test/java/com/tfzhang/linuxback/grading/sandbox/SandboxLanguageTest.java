package com.tfzhang.linuxback.grading.sandbox;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxLanguageTest {

    @Test
    void fromCode_exactCatalogOnly() {
        assertEquals(SandboxLanguage.BASH, SandboxLanguage.fromCode("bash").orElseThrow());
        assertEquals(SandboxLanguage.C, SandboxLanguage.fromCode("c").orElseThrow());
        assertTrue(SandboxLanguage.fromCode("Bash").isEmpty());
        assertTrue(SandboxLanguage.fromCode("python").isEmpty());
        assertTrue(SandboxLanguage.fromCode(null).isEmpty());
        assertTrue(SandboxLanguage.fromCode("").isEmpty());
    }

    @Test
    void codes_matchEnumOrder() {
        assertEquals(List.of("bash", "c"), SandboxLanguage.codes());
        assertEquals("main.sh", SandboxLanguage.BASH.workFileName());
        assertEquals("main.c", SandboxLanguage.C.workFileName());
        assertFalse(SandboxLanguage.BASH.needsCompileStep());
        assertTrue(SandboxLanguage.C.needsCompileStep());
        assertEquals("sandbox-bash:latest", SandboxLanguage.BASH.defaultImage());
        assertEquals("sandbox-c:latest", SandboxLanguage.C.defaultImage());
    }
}
