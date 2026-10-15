package com.tfzhang.linuxback.scheduler;

import com.tfzhang.linuxback.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentStatusScheduler {

    private final ExperimentService experimentService;

    @Scheduled(fixedDelayString = "${experiment.expire-delay-ms:60000}")
    public void expirePublishedPastDeadlines() {
        experimentService.expirePublishedPastDeadlines();
    }
}
