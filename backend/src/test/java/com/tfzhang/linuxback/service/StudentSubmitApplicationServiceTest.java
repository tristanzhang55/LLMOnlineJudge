package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.dto.StudentSubmitRequest;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import com.tfzhang.linuxback.vo.ConfirmSubmitItemVO;
import com.tfzhang.linuxback.vo.ConfirmSubmitVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSubmitApplicationServiceTest {

    @Mock
    private StudentExperimentService studentExperimentService;
    @Mock
    private GradingQueuePublisher gradingQueuePublisher;

    @InjectMocks
    private StudentSubmitApplicationService applicationService;

    @Test
    void confirm_publishesAfterFinalizeReturns() {
        StudentSubmitRequest request = new StudentSubmitRequest();
        ConfirmSubmitVO vo = new ConfirmSubmitVO();
        vo.setSubmitTime(LocalDateTime.of(2026, 8, 26, 8, 0));
        vo.setSubmissions(List.of(
                new ConfirmSubmitItemVO(41L, 900L),
                new ConfirmSubmitItemVO(42L, 901L)));
        when(studentExperimentService.finalizeSubmit(5L, request)).thenReturn(vo);

        applicationService.confirm(5L, request);

        InOrder order = inOrder(studentExperimentService, gradingQueuePublisher);
        order.verify(studentExperimentService).finalizeSubmit(5L, request);
        order.verify(gradingQueuePublisher).publishSubmission(900L);
        order.verify(gradingQueuePublisher).publishSubmission(901L);
        verify(gradingQueuePublisher).publishSubmission(900L);
    }
}
