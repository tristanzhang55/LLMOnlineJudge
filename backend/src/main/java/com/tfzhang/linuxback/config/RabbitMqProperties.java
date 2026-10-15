package com.tfzhang.linuxback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMqProperties {

    private boolean enabled = true;
    private String host = "localhost";
    private int port = 5672;
    private String username = "guest";
    private String password = "guest";
    private String virtualHost = "/";
    private String gradingQueue = "submission-grading-queue";
    private String gradingDlq = "submission-grading-queue.dlq";
    private String gradingDlx = "submission-grading-dlx";
}
