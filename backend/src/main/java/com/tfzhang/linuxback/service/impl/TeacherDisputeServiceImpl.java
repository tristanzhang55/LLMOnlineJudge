package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.dto.DisputeForwardRequest;
import com.tfzhang.linuxback.dto.DisputeResolveRequest;
import com.tfzhang.linuxback.dto.DisputeResolveScoreItem;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.TeacherDisputeService;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.TeacherDisputeForwardItemVO;
import com.tfzhang.linuxback.vo.TeacherDisputeForwardResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherDisputeServiceImpl implements TeacherDisputeService {

    static final String FORWARDED = "forwarded";
    static final String ALREADY_RESOLVED = "already_resolved";
    static final String ALREADY_WITHDRAWN = "already_withdrawn";
    static final String NOT_FOUND = "not_found";
    static final String CONFLICT = "conflict";

    private final DisputeMapper disputeMapper;
    private final SubmissionMapper submissionMapper;
    private final ExperimentQuestionMapper experimentQuestionMapper;
    private final ExperimentService experimentService;
    private final SubQuestionMapper subQuestionMapper;
    private final SubQuestionScoreMapper subQuestionScoreMapper;
    private final GradingQueuePublisher gradingQueuePublisher;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public DisputeVO resolve(long disputeId, DisputeResolveRequest request) {
        OwnedDispute owned = loadOwned(disputeId);
        if (owned == null) {
            throw ExperimentException.notFound();
        }
        validateScores(owned.experimentQuestion, request.getSubQuestionScores());

        LocalDateTime now = LocalDateTime.now();
        int affected = disputeMapper.casResolve(owned.dispute.getId(), request.getManualComment(), now);
        if (affected == 0) {
            throwResolveCasConflict(disputeId);
        }

        for (DisputeResolveScoreItem item : request.getSubQuestionScores()) {
            subQuestionScoreMapper.upsertManualScore(
                    owned.submission.getId(),
                    item.getSubQuestionId(),
                    item.getManualScore(),
                    item.getManualScore());
        }

        recomputeScore(owned.submission.getId());
        recomputeIsManualOverride(owned.submission.getId());
        if (owned.submission.getStatus() != null
                && owned.submission.getStatus() == 3
                && subQuestionScoreMapper.countBySubmissionId(owned.submission.getId()) > 0) {
            LambdaUpdateWrapper<Submission> status = new LambdaUpdateWrapper<>();
            status.eq(Submission::getId, owned.submission.getId())
                    .eq(Submission::getStatus, 3)
                    .set(Submission::getStatus, 2);
            submissionMapper.update(null, status);
        }

        Dispute updated = disputeMapper.selectById(disputeId);
        if (updated == null) {
            throw ExperimentException.notFound();
        }
        updated.setManualComment(request.getManualComment());
        updated.setResolveTime(now);
        return DisputeStatus.toVo(updated);
    }

    @Override
    public TeacherDisputeForwardResultVO forward(DisputeForwardRequest request) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        TeacherDisputeForwardResultVO vo = new TeacherDisputeForwardResultVO();
        List<TeacherDisputeForwardItemVO> results = new ArrayList<>();
        for (Long disputeId : request.getDisputeIds()) {
            ForwardItemOutcome outcome = tx.execute(status -> forwardOne(disputeId, request.getNote()));
            if (outcome == null) {
                results.add(item(disputeId, NOT_FOUND));
                continue;
            }
            results.add(outcome.item());
            if (outcome.publishSubmissionId() != null) {
                gradingQueuePublisher.publishSubmission(outcome.publishSubmissionId());
            }
        }
        vo.setResults(results);
        return vo;
    }

    ForwardItemOutcome forwardOne(long disputeId, String note) {
        OwnedDispute owned = loadOwned(disputeId);
        if (owned == null) {
            return new ForwardItemOutcome(item(disputeId, NOT_FOUND), null);
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = disputeMapper.casResolve(owned.dispute.getId(), note, now);
        if (affected == 0) {
            Dispute current = disputeMapper.selectById(disputeId);
            if (current == null) {
                return new ForwardItemOutcome(item(disputeId, NOT_FOUND), null);
            }
            if (current.getResolveTime() != null) {
                return new ForwardItemOutcome(item(disputeId, ALREADY_RESOLVED), null);
            }
            if (current.getWithdrawnTime() != null) {
                return new ForwardItemOutcome(item(disputeId, ALREADY_WITHDRAWN), null);
            }
            return new ForwardItemOutcome(item(disputeId, NOT_FOUND), null);
        }

        long submissionId = owned.submission.getId();
        subQuestionScoreMapper.clearManualScores(submissionId);
        recomputeIsManualOverride(submissionId);
        int reset = submissionMapper.resetForRegrade(submissionId);
        if (reset == 0) {
            return new ForwardItemOutcome(item(disputeId, CONFLICT), null);
        }
        return new ForwardItemOutcome(item(disputeId, FORWARDED), submissionId);
    }

    private void throwResolveCasConflict(long disputeId) {
        Dispute current = disputeMapper.selectById(disputeId);
        if (current == null) {
            throw ExperimentException.notFound();
        }
        if (current.getResolveTime() != null) {
            throw ExperimentException.disputeAlreadyHandled();
        }
        if (current.getWithdrawnTime() != null) {
            throw ExperimentException.disputeAlreadyWithdrawn();
        }
        throw ExperimentException.notFound();
    }

    private OwnedDispute loadOwned(long disputeId) {
        Dispute dispute = disputeMapper.selectById(disputeId);
        if (dispute == null || dispute.getSubmissionId() == null) {
            return null;
        }
        Submission submission = submissionMapper.selectById(dispute.getSubmissionId());
        if (submission == null || submission.getExperimentQuestionId() == null) {
            return null;
        }
        ExperimentQuestion experimentQuestion =
                experimentQuestionMapper.selectById(submission.getExperimentQuestionId());
        if (experimentQuestion == null || experimentQuestion.getExperimentId() == null) {
            return null;
        }
        try {
            experimentService.requireOwnedExperiment(experimentQuestion.getExperimentId());
        } catch (ExperimentException ex) {
            if ("NOT_FOUND".equals(ex.getCode())) {
                return null;
            }
            throw ex;
        }
        return new OwnedDispute(dispute, submission, experimentQuestion);
    }

    private void validateScores(ExperimentQuestion experimentQuestion, List<DisputeResolveScoreItem> scores) {
        if (scores.isEmpty()) {
            return;
        }
        if (experimentQuestion.getQuestionId() == null) {
            throw ExperimentException.notFound();
        }
        LambdaQueryWrapper<SubQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubQuestion::getQuestionId, experimentQuestion.getQuestionId());
        Map<Long, SubQuestion> byId = subQuestionMapper.selectList(wrapper).stream()
                .filter(sq -> sq.getId() != null)
                .collect(Collectors.toMap(SubQuestion::getId, sq -> sq, (a, b) -> a));
        for (DisputeResolveScoreItem item : scores) {
            SubQuestion bank = byId.get(item.getSubQuestionId());
            if (bank == null) {
                throw ExperimentException.resolveSubQuestionNotOnQuestion(item.getSubQuestionId());
            }
            BigDecimal max = bank.getScore() == null ? BigDecimal.ZERO : BigDecimal.valueOf(bank.getScore());
            BigDecimal manual = item.getManualScore();
            if (manual.compareTo(BigDecimal.ZERO) < 0 || manual.compareTo(max) > 0) {
                throw ExperimentException.resolveManualScoreOutOfRange(item.getSubQuestionId());
            }
        }
    }

    void recomputeIsManualOverride(long submissionId) {
        int flag = subQuestionScoreMapper.existsManualScore(submissionId) ? 1 : 0;
        LambdaUpdateWrapper<Submission> update = new LambdaUpdateWrapper<>();
        update.eq(Submission::getId, submissionId)
                .set(Submission::getIsManualOverride, flag);
        submissionMapper.update(null, update);
    }

    void recomputeScore(long submissionId) {
        BigDecimal sum = subQuestionScoreMapper.sumEffectiveScore(submissionId);
        int score = sum == null ? 0 : sum.intValue();
        LambdaUpdateWrapper<Submission> update = new LambdaUpdateWrapper<>();
        update.eq(Submission::getId, submissionId)
                .set(Submission::getScore, score);
        submissionMapper.update(null, update);
    }

    private static TeacherDisputeForwardItemVO item(long disputeId, String status) {
        TeacherDisputeForwardItemVO vo = new TeacherDisputeForwardItemVO();
        vo.setDisputeId(disputeId);
        vo.setStatus(status);
        return vo;
    }

    record OwnedDispute(Dispute dispute, Submission submission, ExperimentQuestion experimentQuestion) {
    }

    record ForwardItemOutcome(TeacherDisputeForwardItemVO item, Long publishSubmissionId) {
    }
}
