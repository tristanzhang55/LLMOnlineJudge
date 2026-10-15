package com.tfzhang.linuxback.grading.sandbox;

import com.tfzhang.linuxback.config.GradingProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@code docker run} argv. Package-visible so tests can assert the list
 * without spawning Docker.
 */
final class DockerRunArgv {

    private DockerRunArgv() {
    }

    static List<String> build(GradingProperties.Docker docker, SandboxLanguage language, Path sourceDir) {
        List<String> argv = new ArrayList<>();
        argv.add(docker.getBinary() == null || docker.getBinary().isBlank() ? "docker" : docker.getBinary());
        argv.add("run");
        argv.add("--rm");
        argv.add("--network");
        argv.add("none");
        argv.add("--read-only");
        argv.add("--tmpfs");
        argv.add("/tmp:rw,noexec,nosuid,size=" + docker.getTmpfsSize());
        if (language.needsCompileStep()) {
            argv.add("--tmpfs");
            argv.add("/sandbox-run:rw,exec,nosuid,size=" + docker.getTmpfsSize() + ",mode=1777");
        }
        argv.add("--memory=" + docker.getMemory());
        argv.add("--memory-swap=" + docker.getMemory());
        argv.add("--pids-limit=" + docker.getPidsLimit());
        argv.add("--cpus=" + docker.getCpus());
        argv.add("-e");
        argv.add("SANDBOX_TIMEOUT_SECS=" + docker.getTimeoutSecs());
        argv.add("-e");
        argv.add("SANDBOX_COMPILE_TIMEOUT_SECS=" + docker.getCompileTimeoutSecs());
        argv.add("-v");
        argv.add(sourceDir.toAbsolutePath() + ":/work:ro");
        argv.add(imageTag(docker, language));
        return List.copyOf(argv);
    }

    static String imageTag(GradingProperties.Docker docker, SandboxLanguage language) {
        String configured = switch (language) {
            case BASH -> docker.getImages().getBash();
            case C -> docker.getImages().getC();
        };
        if (configured == null || configured.isBlank()) {
            return language.defaultImage();
        }
        return configured;
    }
}
