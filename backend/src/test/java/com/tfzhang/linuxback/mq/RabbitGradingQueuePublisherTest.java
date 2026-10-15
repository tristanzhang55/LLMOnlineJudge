package com.tfzhang.linuxback.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RabbitGradingQueuePublisherTest {

    @Test
    void publishSubmission_writesPersistentJsonOnDefaultExchange() throws Exception {
        Channel channel = mock(Channel.class);
        RabbitGradingQueuePublisher publisher = new RabbitGradingQueuePublisher(
                channel, "submission-grading-queue", new ObjectMapper());

        publisher.publishSubmission(1L);

        verify(channel).basicPublish(
                eq(""),
                eq("submission-grading-queue"),
                eq(MessageProperties.PERSISTENT_TEXT_PLAIN),
                eq("{\"submissionId\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void publishSubmission_concurrentCallsDoNotThrow() throws Exception {
        Channel channel = mock(Channel.class);
        RabbitGradingQueuePublisher publisher = new RabbitGradingQueuePublisher(
                channel, "submission-grading-queue", new ObjectMapper());

        int n = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            long id = 1000L + i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    publisher.publishSubmission(id);
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        start.countDown();
        done.await();

        assertEquals(0, failures.get());
        verify(channel, times(n)).basicPublish(
                eq(""),
                eq("submission-grading-queue"),
                eq(MessageProperties.PERSISTENT_TEXT_PLAIN),
                any(byte[].class));
    }
}
