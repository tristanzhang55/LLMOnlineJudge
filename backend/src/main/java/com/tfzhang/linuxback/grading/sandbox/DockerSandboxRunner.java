package com.tfzhang.linuxback.grading.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tfzhang.linuxback.config.GradingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "app.grading.sandbox", havingValue = "docker")
public class DockerSandboxRunner implements SandboxRunner {

    @FunctionalInterface
    interface ProcessLauncher {
        Process start(ProcessBuilder builder) throws IOException;
    }

    private static final ObjectMapper LOG_MAPPER = new ObjectMapper();

    private final GradingProperties gradingProperties;
    private final ProcessLauncher processLauncher;

    @Autowired
    public DockerSandboxRunner(GradingProperties gradingProperties) {
        this(gradingProperties, ProcessBuilder::start);
    }

    DockerSandboxRunner(GradingProperties gradingProperties, ProcessLauncher processLauncher) {
        this.gradingProperties = gradingProperties;
        this.processLauncher = processLauncher;
    }

    @Override
    public Result run(String source, String language) {
        SandboxLanguage catalog = SandboxLanguage.fromCode(language).orElse(null);
        if (catalog == null) {
            return new Result(false, "language missing", null);
        }
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("sandbox-work-");
            Path sourceFile = workDir.resolve(catalog.workFileName());
            Files.writeString(sourceFile, source == null ? "" : source, StandardCharsets.UTF_8);
            GradingProperties.Docker docker = gradingProperties.getDocker();
            List<String> argv = DockerRunArgv.build(docker, catalog, workDir);
            ProcessBuilder builder = new ProcessBuilder(argv);
            // Redirect.DISCARD is output-only; NUL/devnull is the stdin equivalent.
            builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
            Process process = processLauncher.start(builder);
            CompletableFuture<ProcessStreamDrainer.Captured> draining = ProcessStreamDrainer.start(process);
            long waitSecs = outerWaitSecs(docker);
            boolean finished;
            try {
                finished = process.waitFor(waitSecs, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                joinQuietly(draining);
                throw new IllegalStateException("interrupted waiting for sandbox", e);
            }
            if (!finished) {
                process.destroyForcibly();
                joinQuietly(draining);
                throw new IllegalStateException("sandbox wait timed out");
            }
            ProcessStreamDrainer.Captured captured = ProcessStreamDrainer.join(draining);
            SandboxRunner.SandboxOutcome parsed;
            try {
                parsed = SandboxJsonParser.parseLastLine(captured.stdout());
            } catch (IllegalStateException e) {
                String cliErr = new String(captured.stderr() == null ? new byte[0] : captured.stderr(),
                        StandardCharsets.UTF_8);
                throw new IllegalStateException(
                        "sandbox last-line JSON missing or malformed: " + cliErr, e);
            }
            Utf8FieldTruncator.Truncation truncated = Utf8FieldTruncator.truncate(parsed);
            return new Result(true, gradingLog(truncated), truncated.outcome());
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("sandbox I/O failed", e);
        } finally {
            deleteRecursively(workDir);
        }
    }

    static File nullDevice() {
        String os = System.getProperty("os.name", "");
        return new File(os.toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null");
    }

    static long outerWaitSecs(GradingProperties.Docker docker) {
        return (long) docker.getTimeoutSecs() + docker.getCompileTimeoutSecs() + docker.getWaitSlackSecs();
    }

    private static String gradingLog(Utf8FieldTruncator.Truncation truncated) {
        SandboxOutcome outcome = truncated.outcome();
        ObjectNode node = LOG_MAPPER.createObjectNode();
        if (outcome.compileError() == null) {
            node.putNull("compileError");
        } else {
            node.put("compileError", outcome.compileError());
        }
        node.put("stdout", outcome.stdout());
        node.put("stderr", outcome.stderr());
        if (outcome.exitCode() == null) {
            node.putNull("exitCode");
        } else {
            node.put("exitCode", outcome.exitCode());
        }
        node.put("timedOut", outcome.timedOut());
        try {
            String json = LOG_MAPPER.writeValueAsString(node);
            if (truncated.truncatedFields().isEmpty()) {
                return json;
            }
            return json + " truncated=" + String.join(",", truncated.truncatedFields());
        } catch (IOException e) {
            throw new IllegalStateException("failed to serialize sandbox log", e);
        }
    }

    private static void joinQuietly(CompletableFuture<ProcessStreamDrainer.Captured> draining) {
        try {
            ProcessStreamDrainer.join(draining);
        } catch (IllegalStateException ignored) {
            // already folding into the timeout/interrupt path
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
