package com.tfzhang.linuxback.scheduler;

import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingClaimTimeoutSchedulerTest {

    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private GradingQueuePublisher gradingQueuePublisher;

    @Test
    void resetStaleClaims_resetsAndPublishes() {
        GradingProperties properties = new GradingProperties();
        properties.setClaimTimeoutMinutes(20);
        GradingClaimTimeoutScheduler scheduler = new GradingClaimTimeoutScheduler(
                submissionMapper, gradingQueuePublisher, properties);
        when(submissionMapper.findStaleClaimedSubmissionIds(any())).thenReturn(List.of(44L));
        when(submissionMapper.resetStaleClaim(44L)).thenReturn(1);

        scheduler.resetStaleClaims();

        verify(submissionMapper).resetStaleClaim(44L);
        verify(gradingQueuePublisher).publishSubmission(44L);
    }

    @Test
    void resetStaleClaims_skipsPublishWhenCasLoses() {
        GradingProperties properties = new GradingProperties();
        GradingClaimTimeoutScheduler scheduler = new GradingClaimTimeoutScheduler(
                submissionMapper, gradingQueuePublisher, properties);
        when(submissionMapper.findStaleClaimedSubmissionIds(any())).thenReturn(List.of(44L));
        when(submissionMapper.resetStaleClaim(44L)).thenReturn(0);

        scheduler.resetStaleClaims();

        verify(gradingQueuePublisher, never()).publishSubmission(anyLong());
    }
}
