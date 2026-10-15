package com.tfzhang.linuxback.grading.sandbox;

public interface SandboxRunner {

    /**
     * 013 fields after the 8 KiB courtesy truncate. {@code null} on {@code Result}
     * when {@code ran} is false.
     */
    record SandboxOutcome(
            String compileError,
            String stdout,
            String stderr,
            Integer exitCode,
            boolean timedOut) {
    }

    record Result(boolean ran, String log, SandboxOutcome outcome) {
        public Result(boolean ran, String log) {
            this(ran, log, null);
        }
    }

    /**
     * @param language {@code sub_question.language} (catalog code), never
     *                 student {@code answerBlocks} language
     */
    Result run(String source, String language);
}
