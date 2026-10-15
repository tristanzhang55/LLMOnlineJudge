package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.SubmissionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SubmissionQueryImpl implements SubmissionQuery {

    private final SubmissionMapper submissionMapper;

    @Override
    public boolean hasSubmissionsForExperimentQuestion(long experimentQuestionId) {
        return submissionMapper.existsForExperimentQuestion(experimentQuestionId);
    }

    @Override
    public Set<Long> findExperimentQuestionIdsWithSubmissions(Collection<Long> experimentQuestionIds) {
        if (experimentQuestionIds == null || experimentQuestionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(submissionMapper.findExperimentQuestionIdsWithSubmissions(experimentQuestionIds));
    }

    @Override
    public long countDistinctStudentsForExperiment(long experimentId) {
        return submissionMapper.countDistinctStudentsForExperiment(experimentId);
    }

    @Override
    public boolean isLocked(long experimentId, long studentId) {
        return submissionMapper.isLockedForCurrentRound(experimentId, studentId);
    }

    @Override
    public boolean isResubmissionEligible(Experiment experiment, long studentId) {
        if (experiment == null || experiment.getStatus() == null || experiment.getStatus() != 1) {
            return false;
        }
        if (experiment.getLastPublishedTime() == null) {
            return false;
        }
        if (isLocked(experiment.getId(), studentId)) {
            return false;
        }
        return submissionMapper.hasPriorRoundSubmissions(experiment.getId(), studentId);
    }

    @Override
    public Submission getCurrentSubmission(long experimentQuestionId, long studentId) {
        return submissionMapper.findCurrentSubmission(experimentQuestionId, studentId);
    }

    @Override
    public Submission getLatestSubmissionForPrefill(long experimentQuestionId, long studentId) {
        return submissionMapper.findLatestSubmission(experimentQuestionId, studentId);
    }

    @Override
    public Submission findDraft(long experimentQuestionId, long studentId) {
        return submissionMapper.findDraft(experimentQuestionId, studentId);
    }

    @Override
    public boolean isRoundGradingComplete(long experimentId, long studentId) {
        if (!isLocked(experimentId, studentId)) {
            return false;
        }
        return submissionMapper.isRoundGradingComplete(experimentId, studentId);
    }
}
