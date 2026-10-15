package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.tfzhang.linuxback.config.RabbitMqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingQueueConsumerTest {

    @Mock
    private Channel consumerChannel;
    @Mock
    private GradingWorkerService gradingWorkerService;

    private GradingQueueConsumer consumer;

    @BeforeEach
    void setUp() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setGradingQueue("submission-grading-queue");
        consumer = new GradingQueueConsumer(
                consumerChannel, properties, gradingWorkerService, new ObjectMapper());
    }

    @Test
    void parseSubmissionId_readsJson() {
        assertEquals(900L, consumer.parseSubmissionId("{\"submissionId\":900}"));
        assertNull(consumer.parseSubmissionId("not-json"));
        assertNull(consumer.parseSubmissionId("{}"));
    }

    @Test
    void start_acksDuplicatesAndCompleted() throws Exception {
        when(consumerChannel.basicConsume(
                        anyString(), eq(false), any(DeliverCallback.class), any(CancelCallback.class)))
                .thenReturn("ctag");
        when(gradingWorkerService.handle(900L)).thenReturn(GradingOutcome.DUPLICATE);

        consumer.start();

        ArgumentCaptor<DeliverCallback> captor = ArgumentCaptor.forClass(DeliverCallback.class);
        verify(consumerChannel).basicConsume(
                eq("submission-grading-queue"), eq(false), captor.capture(), any(CancelCallback.class));
        captor.getValue().handle("", delivery(900L, 7L));
        verify(consumerChannel).basicAck(7L, false);
    }

    @Test
    void start_nacksPermanentWithoutRequeue() throws Exception {
        when(consumerChannel.basicConsume(
                        anyString(), eq(false), any(DeliverCallback.class), any(CancelCallback.class)))
                .thenReturn("ctag");
        when(gradingWorkerService.handle(900L)).thenReturn(GradingOutcome.PERMANENT_FAILURE);

        consumer.start();

        ArgumentCaptor<DeliverCallback> captor = ArgumentCaptor.forClass(DeliverCallback.class);
        verify(consumerChannel).basicConsume(
                eq("submission-grading-queue"), eq(false), captor.capture(), any(CancelCallback.class));
        captor.getValue().handle("", delivery(900L, 8L));
        verify(consumerChannel).basicNack(8L, false, false);
    }

    private static Delivery delivery(long submissionId, long tag) {
        Envelope envelope = new Envelope(tag, false, "", "submission-grading-queue");
        byte[] body = ("{\"submissionId\":" + submissionId + "}").getBytes(StandardCharsets.UTF_8);
        return new Delivery(envelope, null, body);
    }
}
