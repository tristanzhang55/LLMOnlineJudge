package com.tfzhang.linuxback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.grading")
public class GradingProperties {

    /** fake | llm — only one SubQuestionGrader bean is registered. */
    private String scorer = "fake";
    private long republishDelayMs = 300_000L;
    private int republishAgeMinutes = 10;
    /** How long a status=1 claim may sit before reset to 0. */
    private int claimTimeoutMinutes = 20;
    private long claimTimeoutDelayMs = 300_000L;
    /** Inclusive lower bound for fake scorer sleep, milliseconds. */
    private int fakeScoreDelayMinMs = 1000;
    /** Exclusive upper bound for fake scorer sleep, milliseconds. */
    private int fakeScoreDelayMaxMs = 5001;
    /** noop | docker — independent of {@code scorer}. */
    private String sandbox = "noop";
    private Docker docker = new Docker();
    private Llm llm = new Llm();
    private Ocr ocr = new Ocr();

    @Data
    public static class Llm {
        private String apiKey = "";
        private String model = "qwen-plus";
        private int timeoutMs = 60_000;
    }

    @Data
    public static class Ocr {
        private String model = "qwen-vl-ocr";
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private int timeoutMs = 60_000;
        private int minPixels = 3072;
        private int maxPixels = 12_845_056;
    }

    @Data
    public static class Docker {
        private String binary = "docker";
        private int timeoutSecs = 10;
        private int compileTimeoutSecs = 10;
        private int waitSlackSecs = 15;
        private String memory = "128m";
        private int pidsLimit = 64;
        private String cpus = "1";
        private String tmpfsSize = "16m";
        private Images images = new Images();

        @Data
        public static class Images {
            private String bash = "sandbox-bash:latest";
            private String c = "sandbox-c:latest";
        }
    }
}
