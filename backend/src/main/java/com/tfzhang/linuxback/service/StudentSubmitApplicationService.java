package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.dto.StudentSubmitRequest;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import com.tfzhang.linuxback.vo.ConfirmSubmitItemVO;
import com.tfzhang.linuxback.vo.ConfirmSubmitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentSubmitApplicationService {

    private final StudentExperimentService studentExperimentService;
    private final GradingQueuePublisher gradingQueuePublisher;

    public ConfirmSubmitVO confirm(long experimentId, StudentSubmitRequest request) {
        ConfirmSubmitVO vo = studentExperimentService.finalizeSubmit(experimentId, request);
        if (vo.getSubmissions() != null) {
            for (ConfirmSubmitItemVO item : vo.getSubmissions()) {
                gradingQueuePublisher.publishSubmission(item.getSubmissionId());
            }
        }
        return vo;
    }
}
