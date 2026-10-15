package com.tfzhang.linuxback.scheduler;

import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingRepublishSchedulerTest {

    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private GradingQueuePublisher gradingQueuePublisher;

    @Test
    void republishStalePending_usesStatusZeroAgeCutoff() {
        GradingProperties properties = new GradingProperties();
        properties.setRepublishAgeMinutes(10);
        GradingRepublishScheduler scheduler = new GradingRepublishScheduler(
                submissionMapper, gradingQueuePublisher, properties);
        when(submissionMapper.findStalePendingSubmissionIds(any())).thenReturn(List.of(9L));

        LocalDateTime before = LocalDateTime.now().minusMinutes(10);
        scheduler.republishStalePending();
        LocalDateTime after = LocalDateTime.now().minusMinutes(10);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(submissionMapper).findStalePendingSubmissionIds(cutoff.capture());
        assertTrue(!cutoff.getValue().isBefore(before.minusSeconds(2)));
        assertTrue(!cutoff.getValue().isAfter(after.plusSeconds(2)));
        verify(gradingQueuePublisher).publishSubmission(9L);
    }

    @Test
    void republishStalePending_doesNotPublishWhenMapperReturnsEmpty() {
        GradingProperties properties = new GradingProperties();
        GradingRepublishScheduler scheduler = new GradingRepublishScheduler(
                submissionMapper, gradingQueuePublisher, properties);
        when(submissionMapper.findStalePendingSubmissionIds(any())).thenReturn(List.of());

        scheduler.republishStalePending();

        verify(gradingQueuePublisher, never()).publishSubmission(org.mockito.ArgumentMatchers.anyLong());
    }
}
