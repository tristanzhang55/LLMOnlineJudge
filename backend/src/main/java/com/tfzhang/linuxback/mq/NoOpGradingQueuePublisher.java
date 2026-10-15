package com.tfzhang.linuxback.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpGradingQueuePublisher implements GradingQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpGradingQueuePublisher.class);

    @Override
    public void publishSubmission(long submissionId) {
        log.debug("RabbitMQ disabled; skipping publish for submissionId={}", submissionId);
    }
}
