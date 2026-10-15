package com.tfzhang.linuxback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String type = "local";
    private Local local = new Local();
    private TencentCos tencentCos = new TencentCos();

    @Data
    public static class Local {
        private String basePath = "./uploads";
        private String urlPrefix = "/files";
    }

    @Data
    public static class TencentCos {
        private String secretId = "";
        private String secretKey = "";
        private String region = "ap-shanghai";
        private String bucket = "linuxjudge-1301303075";
    }
}
