package com.tfzhang.linuxback.grading.sandbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.grading.sandbox", havingValue = "noop", matchIfMissing = true)
public class NoOpSandboxRunner implements SandboxRunner {

    @Override
    public Result run(String source, String language) {
        return new Result(false, "sandbox not wired");
    }
}
