package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.StudentDisputeListRow;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.StudentDisputeService;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.StudentDisputeListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentDisputeServiceImpl implements StudentDisputeService {

    private final DisputeMapper disputeMapper;
    private final SubmissionMapper submissionMapper;

    @Override
    public PageResult<StudentDisputeListItemVO> listDisputes(int page, int pageSize) {
        long studentId = CurrentUser.requireId();
        int offset = (page - 1) * pageSize;
        long total = disputeMapper.countStudentDisputes(studentId);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        List<StudentDisputeListItemVO> data = disputeMapper
                .findStudentDisputes(studentId, pageSize, offset)
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(data, total);
    }

    @Override
    @Transactional
    public DisputeVO withdraw(long disputeId) {
        Dispute dispute = disputeMapper.selectById(disputeId);
        if (dispute == null || dispute.getSubmissionId() == null) {
            throw ExperimentException.notFound();
        }
        Submission submission = submissionMapper.selectById(dispute.getSubmissionId());
        if (submission == null
                || submission.getStudentId() == null
                || submission.getStudentId() != CurrentUser.requireId()) {
            throw ExperimentException.notFound();
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = disputeMapper.casWithdraw(dispute.getId(), now);
        if (affected == 1) {
            dispute.setWithdrawnTime(now);
            return DisputeStatus.toVo(dispute);
        }

        Dispute current = disputeMapper.selectById(disputeId);
        if (current == null) {
            throw ExperimentException.notFound();
        }
        if (current.getResolveTime() != null) {
            throw ExperimentException.disputeAlreadyResolved();
        }
        if (current.getWithdrawnTime() != null) {
            return DisputeStatus.toVo(current);
        }
        throw ExperimentException.notFound();
    }

    private StudentDisputeListItemVO toVo(StudentDisputeListRow row) {
        StudentDisputeListItemVO vo = new StudentDisputeListItemVO();
        vo.setId(row.getId());
        vo.setSubmissionId(row.getSubmissionId());
        vo.setExperimentId(row.getExperimentId());
        vo.setExperimentTitle(row.getExperimentTitle() == null ? "" : row.getExperimentTitle());
        vo.setExperimentQuestionId(row.getExperimentQuestionId());
        vo.setQuestionTitle(row.getQuestionTitle());
        vo.setDisputeStatus(row.getDisputeStatus());
        vo.setDisputeTime(row.getDisputeTime());
        vo.setScore(scoreForStatus(row.getSubmissionStatus(), row.getScore()));
        return vo;
    }

    static Integer scoreForStatus(Integer submissionStatus, Integer score) {
        if (submissionStatus != null && submissionStatus == 2) {
            return score;
        }
        return null;
    }
}
