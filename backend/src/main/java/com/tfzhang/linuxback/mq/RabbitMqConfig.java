package com.tfzhang.linuxback.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.tfzhang.linuxback.config.RabbitMqProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfig {

    @Bean(destroyMethod = "close")
    Connection rabbitConnection(RabbitMqProperties properties) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(properties.getHost());
        factory.setPort(properties.getPort());
        factory.setUsername(properties.getUsername());
        factory.setPassword(properties.getPassword());
        factory.setVirtualHost(properties.getVirtualHost());
        return factory.newConnection();
    }

    @Bean(name = "rabbitPublisherChannel", destroyMethod = "close")
    Channel rabbitPublisherChannel(Connection rabbitConnection, RabbitMqProperties properties)
            throws IOException {
        Channel channel = rabbitConnection.createChannel();
        declareQueues(channel, properties);
        return channel;
    }

    @Bean(name = "rabbitConsumerChannel", destroyMethod = "close")
    Channel rabbitConsumerChannel(Connection rabbitConnection) throws IOException {
        Channel channel = rabbitConnection.createChannel();
        channel.basicQos(1);
        return channel;
    }

    @Bean
    GradingQueuePublisher gradingQueuePublisher(
            @Qualifier("rabbitPublisherChannel") Channel rabbitPublisherChannel,
            RabbitMqProperties properties,
            ObjectMapper objectMapper) {
        return new RabbitGradingQueuePublisher(
                rabbitPublisherChannel, properties.getGradingQueue(), objectMapper);
    }

    static void declareQueues(Channel channel, RabbitMqProperties properties) throws IOException {
        Map<String, Object> quorum = Map.of("x-queue-type", "quorum");
        channel.exchangeDeclare(properties.getGradingDlx(), BuiltinExchangeType.DIRECT, true);
        channel.queueDeclare(properties.getGradingDlq(), true, false, false, quorum);
        channel.queueBind(
                properties.getGradingDlq(),
                properties.getGradingDlx(),
                properties.getGradingQueue());
        Map<String, Object> workArgs = Map.of(
                "x-queue-type", "quorum",
                "x-dead-letter-exchange", properties.getGradingDlx(),
                "x-dead-letter-routing-key", properties.getGradingQueue());
        channel.queueDeclare(properties.getGradingQueue(), true, false, false, workArgs);
    }
}
