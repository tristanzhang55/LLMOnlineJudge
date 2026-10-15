package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.StudentSubmissionHistoryService;
import com.tfzhang.linuxback.service.SubmissionRoundStatus;
import com.tfzhang.linuxback.vo.StudentSubmissionHistoryItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentSubmissionHistoryServiceImpl implements StudentSubmissionHistoryService {

    private final SubmissionMapper submissionMapper;

    @Override
    public PageResult<StudentSubmissionHistoryItemVO> listRounds(int page, int pageSize) {
        long studentId = CurrentUser.requireId();
        int offset = (page - 1) * pageSize;
        long total = submissionMapper.countSubmissionRounds(studentId);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        List<StudentSubmissionHistoryItemVO> data = submissionMapper
                .findSubmissionRounds(studentId, pageSize, offset)
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(data, total);
    }

    private StudentSubmissionHistoryItemVO toVo(SubmissionRoundAggregateRow row) {
        String status = SubmissionRoundStatus.derive(row);
        StudentSubmissionHistoryItemVO vo = new StudentSubmissionHistoryItemVO();
        vo.setExperimentId(row.getExperimentId());
        vo.setExperimentTitle(row.getExperimentTitle());
        vo.setSubmitTime(row.getSubmitTime());
        vo.setQuestionCount(row.getQuestionCount() == null ? 0 : row.getQuestionCount().intValue());
        vo.setStatus(status);
        vo.setScore(SubmissionRoundStatus.scoreForStatus(status, row.getScoreSum()));
        return vo;
    }
}
