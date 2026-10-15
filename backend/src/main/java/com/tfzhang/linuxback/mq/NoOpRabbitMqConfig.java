package com.tfzhang.linuxback.mq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "false")
public class NoOpRabbitMqConfig {

    @Bean
    GradingQueuePublisher noOpGradingQueuePublisher() {
        return new NoOpGradingQueuePublisher();
    }
}
