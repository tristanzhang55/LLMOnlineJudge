package com.tfzhang.linuxback.grading;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GradingPersistenceServiceImpl implements GradingPersistenceService {

    private final SubQuestionScoreMapper subQuestionScoreMapper;
    private final SubmissionMapper submissionMapper;

    @Override
    @Transactional
    public void saveCompleted(Submission claimed, List<SubQuestionScore> scores, int aiScoreSum) {
        for (SubQuestionScore row : scores) {
            subQuestionScoreMapper.upsertAiGrade(row);
        }
        LambdaUpdateWrapper<Submission> update = new LambdaUpdateWrapper<>();
        update.eq(Submission::getId, claimed.getId())
                .eq(Submission::getStatus, 1)
                .set(Submission::getAiScore, aiScoreSum)
                .set(Submission::getStatus, 2);
        boolean keepManualScore = claimed.getIsManualOverride() != null && claimed.getIsManualOverride() == 1;
        if (!keepManualScore) {
            update.set(Submission::getScore, aiScoreSum);
        }
        submissionMapper.update(null, update);
    }

    @Override
    @Transactional
    public void markFailed(long submissionId) {
        LambdaUpdateWrapper<Submission> update = new LambdaUpdateWrapper<>();
        update.eq(Submission::getId, submissionId)
                .eq(Submission::getStatus, 1)
                .set(Submission::getStatus, 3);
        submissionMapper.update(null, update);
    }
}
