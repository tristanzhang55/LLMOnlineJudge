package com.tfzhang.linuxback.grading.sandbox;

import com.tfzhang.linuxback.config.GradingProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSandboxRunnerTest {

    @Test
    void missingLanguage_doesNotLaunchDocker() {
        AtomicReference<Boolean> launched = new AtomicReference<>(false);
        DockerSandboxRunner runner = new DockerSandboxRunner(new GradingProperties(), builder -> {
            launched.set(true);
            throw new AssertionError("docker must not start");
        });
        SandboxRunner.Result result = runner.run("echo hi", null);
        assertFalse(result.ran());
        assertEquals("language missing", result.log());
        assertNull(result.outcome());
        assertFalse(launched.get());
    }

    @Test
    void validJson_evenWithNonZeroExit_isSuccessIncludingTimedOut() {
        String json = "{\"compileError\":null,\"stdout\":\"partial\\n\",\"stderr\":\"\",\"exitCode\":null,\"timedOut\":true}\n";
        CapturingLauncher launcher = CapturingLauncher.json(json, "cli-noise", 17);
        DockerSandboxRunner runner = new DockerSandboxRunner(new GradingProperties(), launcher);
        SandboxRunner.Result result = runner.run("int main(){for(;;);}", "c");
        assertTrue(result.ran());
        assertNotNull(result.outcome());
        assertTrue(result.outcome().timedOut());
        assertEquals("partial\n", result.outcome().stdout());
        assertTrue(launcher.command.stream().anyMatch(part -> part.contains("/sandbox-run")));
        assertFalse(launcher.command.stream().anyMatch(part -> part.contains("int main")));
        assertTrue(FilesDeletedHint.noHostPathInLog(result.log()));
    }

    @Test
    void missingJson_throwsRegardlessOfExitCode() {
        CapturingLauncher launcher = CapturingLauncher.json("not-contract\n", "docker: image missing", 0);
        DockerSandboxRunner runner = new DockerSandboxRunner(new GradingProperties(), launcher);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.run("echo hi", "bash"));
        assertTrue(ex.getMessage().contains("docker: image missing"));
        assertFalse(launcher.command.stream().anyMatch(part -> part.contains("/sandbox-run")));
    }

    @Test
    void truncationNotedInLog() {
        String huge = "b".repeat(9000);
        String json = "{\"compileError\":null,\"stdout\":\"" + huge + "\",\"stderr\":\"\",\"exitCode\":0,\"timedOut\":false}\n";
        CapturingLauncher launcher = CapturingLauncher.json(json, "", 0);
        DockerSandboxRunner runner = new DockerSandboxRunner(new GradingProperties(), launcher);
        SandboxRunner.Result result = runner.run("echo", "bash");
        assertTrue(result.ran());
        assertEquals(Utf8FieldTruncator.MAX_BYTES,
                result.outcome().stdout().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(result.log().contains("truncated=stdout"));
    }

    @Test
    void outerWaitTimeout_isTransient() {
        CapturingLauncher launcher = CapturingLauncher.hanging();
        DockerSandboxRunner runner = new DockerSandboxRunner(new GradingProperties(), launcher);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.run("echo hi", "bash"));
        assertEquals("sandbox wait timed out", ex.getMessage());
        assertTrue(launcher.process.destroyedForcibly);
    }

    private static final class CapturingLauncher implements DockerSandboxRunner.ProcessLauncher {
        private final FakeDockerProcess process;
        private List<String> command = new ArrayList<>();

        private CapturingLauncher(FakeDockerProcess process) {
            this.process = process;
        }

        static CapturingLauncher json(String stdout, String stderr, int exit) {
            return new CapturingLauncher(new FakeDockerProcess(stdout, stderr, exit, false));
        }

        static CapturingLauncher hanging() {
            return new CapturingLauncher(new FakeDockerProcess("", "", 0, true));
        }

        @Override
        public Process start(ProcessBuilder builder) {
            command = List.copyOf(builder.command());
            ProcessBuilder.Redirect input = builder.redirectInput();
            assertEquals(ProcessBuilder.Redirect.Type.READ, input.type());
            return process;
        }
    }

    private static final class FilesDeletedHint {
        static boolean noHostPathInLog(String log) {
            return !log.contains(Path.of(System.getProperty("java.io.tmpdir")).toString())
                    && !log.contains("sandbox-work-");
        }
    }

    private static final class FakeDockerProcess extends Process {
        private final ByteArrayInputStream stdout;
        private final ByteArrayInputStream stderr;
        private final int exitCode;
        private final boolean hang;
        private boolean destroyedForcibly;

        private FakeDockerProcess(String stdout, String stderr, int exitCode, boolean hang) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
            this.hang = hang;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return !hang;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            destroyedForcibly = true;
            return this;
        }
    }
}
