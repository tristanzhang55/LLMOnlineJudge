package com.tfzhang.linuxback.grading.sandbox;

import com.tfzhang.linuxback.config.GradingProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerRunArgvTest {

    @Test
    void bash_bindWorkRo_timeoutEnv_noSandboxRun() {
        GradingProperties.Docker docker = new GradingProperties.Docker();
        Path dir = Path.of("C:\\tmp\\case-bash");
        List<String> argv = DockerRunArgv.build(docker, SandboxLanguage.BASH, dir);

        assertEquals("docker", argv.get(0));
        assertTrue(argv.contains("--rm"));
        assertTrue(argv.contains("--read-only"));
        assertTrue(argv.contains("/tmp:rw,noexec,nosuid,size=16m"));
        assertTrue(argv.contains("SANDBOX_TIMEOUT_SECS=10"));
        assertTrue(argv.contains("SANDBOX_COMPILE_TIMEOUT_SECS=10"));
        assertTrue(argv.contains(dir.toAbsolutePath() + ":/work:ro"));
        assertEquals("sandbox-bash:latest", argv.get(argv.size() - 1));
        assertFalse(argv.stream().anyMatch(part -> part.contains("/sandbox-run")));
    }

    @Test
    void c_hasSandboxRunTmpfsWithMode1777() {
        GradingProperties.Docker docker = new GradingProperties.Docker();
        Path dir = Path.of("C:\\tmp\\case-c");
        List<String> argv = DockerRunArgv.build(docker, SandboxLanguage.C, dir);

        int tmpIndex = argv.indexOf("/tmp:rw,noexec,nosuid,size=16m");
        int runIndex = argv.indexOf("/sandbox-run:rw,exec,nosuid,size=16m,mode=1777");
        assertTrue(tmpIndex > 0);
        assertTrue(runIndex > tmpIndex);
        assertEquals("--tmpfs", argv.get(runIndex - 1));
        assertEquals("sandbox-c:latest", argv.get(argv.size() - 1));
        assertTrue(argv.contains(dir.toAbsolutePath() + ":/work:ro"));
    }

    @Test
    void blankImageFallsBackToCatalogDefault() {
        GradingProperties.Docker docker = new GradingProperties.Docker();
        docker.getImages().setBash("  ");
        assertEquals("sandbox-bash:latest", DockerRunArgv.imageTag(docker, SandboxLanguage.BASH));
    }
}
