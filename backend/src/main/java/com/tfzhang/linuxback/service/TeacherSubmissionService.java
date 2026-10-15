package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.vo.TeacherDisputeInboxItemVO;
import com.tfzhang.linuxback.vo.TeacherStudentSubmissionsVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionRoundItemVO;

public interface TeacherSubmissionService {

    PageResult<TeacherSubmissionRoundItemVO> listRounds(
            int page, int pageSize, String studentName, Integer classId);

    PageResult<TeacherDisputeInboxItemVO> listDisputes(
            int page, int pageSize, String studentName,
            Integer classId, Integer experimentId, Integer disputeStatus);

    TeacherStudentSubmissionsVO getStudentSubmissions(long experimentId, long studentId);
}
