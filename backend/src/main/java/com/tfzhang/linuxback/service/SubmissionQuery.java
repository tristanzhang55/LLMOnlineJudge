package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.entity.Submission;

import java.util.Collection;
import java.util.Set;

public interface SubmissionQuery {

    boolean hasSubmissionsForExperimentQuestion(long experimentQuestionId);

    Set<Long> findExperimentQuestionIdsWithSubmissions(Collection<Long> experimentQuestionIds);

    long countDistinctStudentsForExperiment(long experimentId);

    boolean isLocked(long experimentId, long studentId);

    boolean isResubmissionEligible(com.tfzhang.linuxback.entity.Experiment experiment, long studentId);

    Submission getCurrentSubmission(long experimentQuestionId, long studentId);

    Submission getLatestSubmissionForPrefill(long experimentQuestionId, long studentId);

    Submission findDraft(long experimentQuestionId, long studentId);

    boolean isRoundGradingComplete(long experimentId, long studentId);
}
