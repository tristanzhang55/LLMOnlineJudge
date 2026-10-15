package com.tfzhang.linuxback.grading;

import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;

import java.util.List;

public interface GradingPersistenceService {

    void saveCompleted(Submission claimed, List<SubQuestionScore> scores, int aiScoreSum);

    void markFailed(long submissionId);
}
