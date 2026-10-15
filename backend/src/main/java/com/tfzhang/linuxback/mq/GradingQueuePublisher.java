package com.tfzhang.linuxback.mq;

public interface GradingQueuePublisher {

    void publishSubmission(long submissionId);
}
