package com.tfzhang.linuxback.grading.sandbox;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Allowed {@code sub_question.language} values. Add a constant to support
 * a new language; do not hardcode the set in DTOs or SQL.
 */
public enum SandboxLanguage {

    BASH("bash", "main.sh", false, "sandbox-bash:latest"),
    C("c", "main.c", true, "sandbox-c:latest");

    private final String code;
    private final String workFileName;
    private final boolean needsCompileStep;
    private final String defaultImage;

    SandboxLanguage(String code, String workFileName, boolean needsCompileStep, String defaultImage) {
        this.code = code;
        this.workFileName = workFileName;
        this.needsCompileStep = needsCompileStep;
        this.defaultImage = defaultImage;
    }

    public String getCode() {
        return code;
    }

    public String workFileName() {
        return workFileName;
    }

    public boolean needsCompileStep() {
        return needsCompileStep;
    }

    public String defaultImage() {
        return defaultImage;
    }

    public static Optional<SandboxLanguage> fromCode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.code.equals(raw))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(SandboxLanguage::getCode).toList();
    }
}
