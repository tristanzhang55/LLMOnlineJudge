package com.tfzhang.linuxback.scheduler;

import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GradingRepublishScheduler {

    private final SubmissionMapper submissionMapper;
    private final GradingQueuePublisher gradingQueuePublisher;
    private final GradingProperties gradingProperties;

    @Scheduled(fixedDelayString = "${app.grading.republish-delay-ms:300000}")
    public void republishStalePending() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(gradingProperties.getRepublishAgeMinutes());
        List<Long> ids = submissionMapper.findStalePendingSubmissionIds(cutoff);
        for (Long id : ids) {
            gradingQueuePublisher.publishSubmission(id);
        }
    }
}
