package com.tfzhang.linuxback.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.ErrorResponse;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.entity.ClassStudent;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.SubmissionDraftService;
import com.tfzhang.linuxback.service.SubmissionQuery;
import com.tfzhang.linuxback.vo.SubmissionDraftVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubmissionDraftServiceImpl implements SubmissionDraftService {

    private final ExperimentQuestionMapper experimentQuestionMapper;
    private final ExperimentMapper experimentMapper;
    private final ClassStudentMapper classStudentMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionQuery submissionQuery;
    private final ExperimentService experimentService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SubmissionDraftVO saveDraft(long experimentQuestionId, JsonNode answerBlocks) {
        long studentId = CurrentUser.requireId();
        ExperimentQuestion eq = experimentQuestionMapper.selectById(experimentQuestionId);
        if (eq == null) {
            throw ExperimentException.validationFailed(List.of(
                    new ErrorResponse.FieldError("experimentQuestionId",
                            Msg.t("error.experiment.experimentQuestionMissing"))));
        }

        experimentService.persistExpiredPublished(eq.getExperimentId(), null);
        Experiment experiment = experimentMapper.selectById(eq.getExperimentId());
        assertStudentCanSeePublished(experiment, studentId);

        if (experiment.getStatus() == null || experiment.getStatus() != 1) {
            throw ExperimentException.draftNotPublished();
        }
        if (submissionQuery.isLocked(experiment.getId(), studentId)) {
            throw ExperimentException.draftLocked();
        }

        long id = persistDraftRow(experimentQuestionId, studentId, toJson(answerBlocks));
        return new SubmissionDraftVO(id);
    }

    @Override
    public long persistDraftRow(long experimentQuestionId, long studentId, String answerBlocksJson) {
        Submission row = new Submission();
        row.setExperimentQuestionId(experimentQuestionId);
        row.setStudentId(studentId);
        row.setAnswerBlocks(answerBlocksJson);
        submissionMapper.upsertDraft(row);
        Submission stored = submissionMapper.findDraft(experimentQuestionId, studentId);
        if (stored == null || stored.getId() == null) {
            throw new IllegalStateException("draft upsert did not produce a row");
        }
        return stored.getId();
    }

    private void assertStudentCanSeePublished(Experiment experiment, long studentId) {
        if (experiment == null || experiment.getStatus() == null || experiment.getStatus() == 0) {
            throw ExperimentException.notFound();
        }
        if (experiment.getClassId() == null || !isEnrolled(experiment.getClassId(), studentId)) {
            throw ExperimentException.notFound();
        }
        if (experiment.getStatus() != 1 && experiment.getStatus() != 2) {
            throw ExperimentException.notFound();
        }
    }

    private boolean isEnrolled(Long classId, long studentId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getClassId, classId).eq(ClassStudent::getStudentId, studentId);
        return classStudentMapper.selectCount(wrapper) > 0;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw ExperimentException.validationFailed(List.of(
                    new ErrorResponse.FieldError("answerBlocks", Msg.t("error.experiment.invalidAnswerBlocks"))));
        }
    }
}
