package com.tfzhang.linuxback.scheduler;

import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GradingClaimTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(GradingClaimTimeoutScheduler.class);

    private final SubmissionMapper submissionMapper;
    private final GradingQueuePublisher gradingQueuePublisher;
    private final GradingProperties gradingProperties;

    @Scheduled(fixedDelayString = "${app.grading.claim-timeout-delay-ms:300000}")
    public void resetStaleClaims() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(gradingProperties.getClaimTimeoutMinutes());
        List<Long> ids = submissionMapper.findStaleClaimedSubmissionIds(cutoff);
        for (Long id : ids) {
            int updated = submissionMapper.resetStaleClaim(id);
            if (updated == 1) {
                log.warn("Reset stale grading claim submissionId={}", id);
                gradingQueuePublisher.publishSubmission(id);
            }
        }
    }
}
