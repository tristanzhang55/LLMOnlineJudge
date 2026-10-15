package com.tfzhang.linuxback.grading.sandbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class NoOpSandboxRunnerTest {

    @Test
    void alwaysSkipped() {
        NoOpSandboxRunner runner = new NoOpSandboxRunner();
        SandboxRunner.Result result = runner.run("echo hi", "bash");
        assertFalse(result.ran());
        assertEquals("sandbox not wired", result.log());
        assertNull(result.outcome());
    }
}
