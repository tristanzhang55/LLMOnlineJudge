package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.tfzhang.linuxback.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class GradingQueueConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GradingQueueConsumer.class);

    private final Channel consumerChannel;
    private final RabbitMqProperties rabbitMqProperties;
    private final GradingWorkerService gradingWorkerService;
    private final ObjectMapper objectMapper;

    private volatile boolean running;
    private String consumerTag;

    public GradingQueueConsumer(
            @Qualifier("rabbitConsumerChannel") Channel consumerChannel,
            RabbitMqProperties rabbitMqProperties,
            GradingWorkerService gradingWorkerService,
            ObjectMapper objectMapper) {
        this.consumerChannel = consumerChannel;
        this.rabbitMqProperties = rabbitMqProperties;
        this.gradingWorkerService = gradingWorkerService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        DeliverCallback deliverCallback = (tag, delivery) -> {
            long deliveryTag = delivery.getEnvelope().getDeliveryTag();
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                Long submissionId = parseSubmissionId(body);
                if (submissionId == null) {
                    log.error("Permanent: grading message missing submissionId: {}", body);
                    consumerChannel.basicNack(deliveryTag, false, false);
                    return;
                }
                GradingOutcome outcome = gradingWorkerService.handle(submissionId);
                if (outcome == GradingOutcome.PERMANENT_FAILURE) {
                    consumerChannel.basicNack(deliveryTag, false, false);
                    return;
                }
                consumerChannel.basicAck(deliveryTag, false);
            } catch (PermanentGradingException e) {
                log.error("Permanent grading failure, nack to DLQ. body={}", body, e);
                consumerChannel.basicNack(deliveryTag, false, false);
            } catch (Exception e) {
                log.error("Transient grading failure; not acking. body={}", body, e);
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
        };
        try {
            CancelCallback onCancel = cancelTag -> log.warn("Grading consumer cancelled: {}", cancelTag);
            consumerTag = consumerChannel.basicConsume(
                    rabbitMqProperties.getGradingQueue(),
                    false,
                    deliverCallback,
                    onCancel);
            running = true;
            log.info("Grading consumer started on {}", rabbitMqProperties.getGradingQueue());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start grading consumer", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (consumerTag != null) {
            try {
                consumerChannel.basicCancel(consumerTag);
            } catch (IOException e) {
                log.warn("Failed to cancel grading consumer", e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    Long parseSubmissionId(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode id = node.get("submissionId");
            if (id == null || id.isNull() || !id.canConvertToLong()) {
                return null;
            }
            return id.asLong();
        } catch (Exception e) {
            return null;
        }
    }
}
