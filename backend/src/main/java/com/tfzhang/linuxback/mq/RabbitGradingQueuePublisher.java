package com.tfzhang.linuxback.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RabbitGradingQueuePublisher implements GradingQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitGradingQueuePublisher.class);

    private final Channel channel;
    private final String gradingQueue;
    private final ObjectMapper objectMapper;

    public RabbitGradingQueuePublisher(Channel channel, String gradingQueue, ObjectMapper objectMapper) {
        this.channel = channel;
        this.gradingQueue = gradingQueue;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishSubmission(long submissionId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("submissionId", submissionId));
            synchronized (channel) {
                channel.basicPublish(
                        "",
                        gradingQueue,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        body.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.error("Failed to publish grading message for submissionId={}", submissionId, e);
        }
    }
}
