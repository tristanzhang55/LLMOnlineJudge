package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.StudentDashboardService;
import com.tfzhang.linuxback.vo.StudentDashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentDashboardServiceImpl implements StudentDashboardService {

    // i18n: persisted content, do not localize
    static final String AUTO_CLOSE_NOTE = "[系统] 作业已重新发布，原申诉自动关闭";

    private final ExperimentService experimentService;
    private final ExperimentMapper experimentMapper;
    private final DisputeMapper disputeMapper;

    @Override
    public StudentDashboardSummaryVO getSummary() {
        long studentId = CurrentUser.requireId();
        experimentService.persistExpiredPublished(null, null);
        StudentDashboardSummaryVO vo = new StudentDashboardSummaryVO();
        vo.setPendingAssignments(experimentMapper.countPendingAssignments(studentId));
        vo.setDisputesWithNewReply(disputeMapper.countUnreadTeacherReplies(studentId, AUTO_CLOSE_NOTE));
        return vo;
    }
}
