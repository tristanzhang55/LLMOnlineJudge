package com.tfzhang.linuxback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface SubQuestionScoreMapper extends BaseMapper<SubQuestionScore> {

    // i18n: persisted content, do not localize
    @Insert("""
            INSERT INTO sub_question_score
                (submissionId, subQuestionId, aiScore, manualScore,
                 effectiveScore, usedSandbox, aiEvidence, gradingLog, gradedAt)
            VALUES
                (#{submissionId}, #{subQuestionId}, NULL, #{manualScore},
                 #{effectiveScore}, 0, NULL,
                 '教师人工评分（原批改未生成该子问题记录）', NOW())
            ON DUPLICATE KEY UPDATE
                manualScore = VALUES(manualScore),
                effectiveScore = VALUES(effectiveScore)
            """)
    int upsertManualScore(
            @Param("submissionId") long submissionId,
            @Param("subQuestionId") long subQuestionId,
            @Param("manualScore") BigDecimal manualScore,
            @Param("effectiveScore") BigDecimal effectiveScore);

    @Insert("""
            INSERT INTO sub_question_score
                (submissionId, subQuestionId, aiScore, manualScore,
                 effectiveScore, usedSandbox, aiEvidence, gradingLog, gradedAt)
            VALUES
                (#{submissionId}, #{subQuestionId}, #{aiScore}, #{manualScore},
                 #{effectiveScore}, #{usedSandbox}, #{aiEvidence}, #{gradingLog}, #{gradedAt})
            ON DUPLICATE KEY UPDATE
                aiScore = VALUES(aiScore),
                aiEvidence = VALUES(aiEvidence),
                gradingLog = VALUES(gradingLog),
                usedSandbox = VALUES(usedSandbox),
                effectiveScore = VALUES(effectiveScore),
                gradedAt = VALUES(gradedAt)
            """)
    int upsertAiGrade(SubQuestionScore row);

    @Update("""
            UPDATE sub_question_score
               SET manualScore = NULL,
                   effectiveScore = aiScore
             WHERE submissionId = #{submissionId}
            """)
    int clearManualScores(@Param("submissionId") long submissionId);

    @Select("""
            SELECT COALESCE(SUM(effectiveScore), 0) FROM sub_question_score
             WHERE submissionId = #{submissionId}
            """)
    BigDecimal sumEffectiveScore(@Param("submissionId") long submissionId);

    @Select("""
            SELECT COUNT(*) > 0 FROM sub_question_score
             WHERE submissionId = #{submissionId}
               AND manualScore IS NOT NULL
            """)
    boolean existsManualScore(@Param("submissionId") long submissionId);

    @Select("""
            SELECT COUNT(*) FROM sub_question_score
             WHERE submissionId = #{submissionId}
            """)
    long countBySubmissionId(@Param("submissionId") long submissionId);
}
