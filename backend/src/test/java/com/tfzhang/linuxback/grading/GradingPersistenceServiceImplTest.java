package com.tfzhang.linuxback.grading;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class GradingPersistenceServiceImplTest {

    @Mock
    private SubQuestionScoreMapper subQuestionScoreMapper;
    @Mock
    private SubmissionMapper submissionMapper;

    private GradingPersistenceServiceImpl persistence;

    @BeforeAll
    static void initMp() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        persistence = new GradingPersistenceServiceImpl(subQuestionScoreMapper, submissionMapper);
    }

    @Test
    void saveCompleted_upsertsAndRewritesScoreWhenNotManualOverride() {
        Submission claimed = claimed(0);
        SubQuestionScore row = scoreRow();

        persistence.saveCompleted(claimed, List.of(row), 7);

        verify(subQuestionScoreMapper).upsertAiGrade(row);
        verifyNoMoreInteractions(subQuestionScoreMapper);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Submission>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(submissionMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("aiScore"));
        assertTrue(sqlSet.contains("status"));
        assertTrue(sqlSet.contains("score"));
    }

    @Test
    void saveCompleted_skipsScoreRewriteWhenManualOverride() {
        Submission claimed = claimed(1);
        SubQuestionScore row = scoreRow();
        row.setManualScore(BigDecimal.TWO);

        persistence.saveCompleted(claimed, List.of(row), 7);

        verify(subQuestionScoreMapper).upsertAiGrade(row);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Submission>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(submissionMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertFalse(sqlSet.contains("score="));
        assertFalse(sqlSet.contains("score,"));
    }

    private static Submission claimed(int isManualOverride) {
        Submission s = new Submission();
        s.setId(900L);
        s.setStatus(1);
        s.setIsManualOverride(isManualOverride);
        return s;
    }

    private static SubQuestionScore scoreRow() {
        SubQuestionScore row = new SubQuestionScore();
        row.setSubmissionId(900L);
        row.setSubQuestionId(101L);
        row.setAiScore(BigDecimal.valueOf(7));
        row.setEffectiveScore(BigDecimal.valueOf(7));
        row.setUsedSandbox(0);
        row.setAiEvidence("ok");
        row.setGradingLog("log");
        row.setGradedAt(LocalDateTime.of(2026, 9, 11, 15, 0));
        return row;
    }
}
