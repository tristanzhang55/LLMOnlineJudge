package com.tfzhang.linuxback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import com.tfzhang.linuxback.entity.Submission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface SubmissionMapper extends BaseMapper<Submission> {

    @Select("""
            SELECT COUNT(*) > 0 FROM submission
            WHERE experimentQuestionId = #{experimentQuestionId}
              AND isDelete = 0
            """)
    boolean existsForExperimentQuestion(@Param("experimentQuestionId") long experimentQuestionId);

    @Select("""
            <script>
            SELECT DISTINCT experimentQuestionId FROM submission
            WHERE isDelete = 0
              AND experimentQuestionId IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<Long> findExperimentQuestionIdsWithSubmissions(@Param("ids") Collection<Long> ids);

    @Select("""
            SELECT COUNT(DISTINCT s.studentId) FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            WHERE eq.experimentId = #{experimentId}
              AND eq.isDelete = 0
              AND s.isDelete = 0
            """)
    long countDistinctStudentsForExperiment(@Param("experimentId") long experimentId);

    @Select("""
            SELECT COUNT(*) > 0 FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            WHERE eq.experimentId = #{experimentId}
              AND s.studentId = #{studentId}
              AND e.lastPublishedTime IS NOT NULL
              AND s.submitTime >= e.lastPublishedTime
              AND s.isDelete = 0
            """)
    boolean isLockedForCurrentRound(
            @Param("experimentId") long experimentId, @Param("studentId") long studentId);

    @Select("""
            SELECT COUNT(*) > 0 FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            WHERE eq.experimentId = #{experimentId}
              AND s.studentId = #{studentId}
              AND e.lastPublishedTime IS NOT NULL
              AND s.submitTime < e.lastPublishedTime
              AND s.isDelete = 0
            """)
    boolean hasPriorRoundSubmissions(
            @Param("experimentId") long experimentId, @Param("studentId") long studentId);

    @Select("""
            SELECT s.* FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            WHERE s.experimentQuestionId = #{experimentQuestionId}
              AND s.studentId = #{studentId}
              AND s.isDelete = 0
              AND e.lastPublishedTime IS NOT NULL
              AND s.submitTime >= e.lastPublishedTime
            ORDER BY s.submitTime DESC, s.id DESC
            LIMIT 1
            """)
    Submission findCurrentSubmission(
            @Param("experimentQuestionId") long experimentQuestionId,
            @Param("studentId") long studentId);

    @Select("""
            SELECT s.* FROM submission s
            WHERE s.experimentQuestionId = #{experimentQuestionId}
              AND s.studentId = #{studentId}
              AND s.isDelete = 0
            ORDER BY s.submitTime DESC, s.id DESC
            LIMIT 1
            """)
    Submission findLatestSubmission(
            @Param("experimentQuestionId") long experimentQuestionId,
            @Param("studentId") long studentId);

    @Select("""
            SELECT s.* FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            WHERE eq.experimentId = #{experimentId}
              AND s.studentId = #{studentId}
              AND s.isDelete = 0
              AND e.lastPublishedTime IS NOT NULL
              AND s.submitTime < e.lastPublishedTime
            ORDER BY s.submitTime DESC, s.id DESC
            """)
    List<Submission> findPriorRoundSubmissions(
            @Param("experimentId") long experimentId, @Param("studentId") long studentId);

    @Select("""
            SELECT s.* FROM submission s
            WHERE s.experimentQuestionId = #{experimentQuestionId}
              AND s.studentId = #{studentId}
              AND s.status = -1
              AND s.isDelete = 0
            LIMIT 1
            """)
    Submission findDraft(
            @Param("experimentQuestionId") long experimentQuestionId,
            @Param("studentId") long studentId);

    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO submission
                (experimentQuestionId, studentId, answerBlocks, status, submitTime,
                 isManualOverride, version, isDelete)
            VALUES (#{experimentQuestionId}, #{studentId}, #{answerBlocks}, -1, NULL, 0, 0, 0)
            ON DUPLICATE KEY UPDATE answerBlocks = VALUES(answerBlocks)
            """)
    int upsertDraft(Submission submission);

    @Select("""
            SELECT COUNT(*) = 0 FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            WHERE eq.experimentId = #{experimentId}
              AND s.studentId = #{studentId}
              AND s.isDelete = 0
              AND e.lastPublishedTime IS NOT NULL
              AND s.submitTime >= e.lastPublishedTime
              AND s.status NOT IN (2, 3)
            """)
    boolean isRoundGradingComplete(
            @Param("experimentId") long experimentId, @Param("studentId") long studentId);

    @Select("""
            SELECT id FROM submission
            WHERE status = 0
              AND isDelete = 0
              AND updateTime < #{cutoff}
            """)
    List<Long> findStalePendingSubmissionIds(@Param("cutoff") java.time.LocalDateTime cutoff);

    @Select("""
            SELECT id FROM submission
            WHERE status = 1
              AND isDelete = 0
              AND updateTime < #{cutoff}
            """)
    List<Long> findStaleClaimedSubmissionIds(@Param("cutoff") java.time.LocalDateTime cutoff);

    @org.apache.ibatis.annotations.Update("""
            UPDATE submission
               SET status = 0, version = version + 1
             WHERE id = #{id}
               AND status = 1
               AND isDelete = 0
            """)
    int resetStaleClaim(@Param("id") long id);

    @Select("""
            SELECT eq.experimentId,
                   e.title AS experimentTitle,
                   s.submitTime,
                   COUNT(*) AS questionCount,
                   SUM(CASE WHEN s.status IN (0, 1) THEN 1 ELSE 0 END) AS pendingOrGradingCount,
                   SUM(CASE WHEN s.status = 1 THEN 1 ELSE 0 END) AS gradingCount,
                   SUM(CASE WHEN s.status = 2 THEN 1 ELSE 0 END) AS doneCount,
                   SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END) AS failedCount,
                   SUM(s.score) AS scoreSum
            FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            WHERE s.studentId = #{studentId}
              AND s.isDelete = 0
              AND s.status IN (0, 1, 2, 3)
            GROUP BY eq.experimentId, s.submitTime, e.title
            ORDER BY s.submitTime DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<SubmissionRoundAggregateRow> findSubmissionRounds(
            @Param("studentId") long studentId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            SELECT COUNT(*) FROM (
                SELECT eq.experimentId, s.submitTime
                FROM submission s
                INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
                INNER JOIN experiment e ON e.id = eq.experimentId
                WHERE s.studentId = #{studentId}
                  AND s.isDelete = 0
                  AND s.status IN (0, 1, 2, 3)
                GROUP BY eq.experimentId, s.submitTime, e.title
            ) t
            """)
    long countSubmissionRounds(@Param("studentId") long studentId);

    @Select("""
            <script>
            SELECT eq.experimentId,
                   e.title AS experimentTitle,
                   e.classId,
                   s.studentId,
                   u.userName AS studentName,
                   s.submitTime,
                   COUNT(*) AS questionCount,
                   SUM(CASE WHEN s.status IN (0, 1) THEN 1 ELSE 0 END) AS pendingOrGradingCount,
                   SUM(CASE WHEN s.status = 1 THEN 1 ELSE 0 END) AS gradingCount,
                   SUM(CASE WHEN s.status = 2 THEN 1 ELSE 0 END) AS doneCount,
                   SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END) AS failedCount,
                   SUM(s.score) AS scoreSum
            FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            INNER JOIN `user` u ON u.id = s.studentId
            WHERE e.userId = #{teacherId}
              AND s.isDelete = 0
              AND s.status IN (0, 1, 2, 3)
              AND u.isDelete = 0
              <if test="classId != null">
                AND e.classId = #{classId}
              </if>
              <if test="studentNameLike != null">
                AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
              </if>
            GROUP BY eq.experimentId, s.studentId, s.submitTime, e.title, e.classId, u.userName
            ORDER BY s.submitTime DESC, s.studentId DESC, eq.experimentId DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SubmissionRoundAggregateRow> findTeacherSubmissionRounds(
            @Param("teacherId") long teacherId,
            @Param("classId") Integer classId,
            @Param("studentNameLike") String studentNameLike,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT eq.experimentId, s.studentId, s.submitTime
                FROM submission s
                INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
                INNER JOIN experiment e ON e.id = eq.experimentId
                INNER JOIN `user` u ON u.id = s.studentId
                WHERE e.userId = #{teacherId}
                  AND s.isDelete = 0
                  AND s.status IN (0, 1, 2, 3)
                  AND u.isDelete = 0
                  <if test="classId != null">
                    AND e.classId = #{classId}
                  </if>
                  <if test="studentNameLike != null">
                    AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
                  </if>
                GROUP BY eq.experimentId, s.studentId, s.submitTime, e.title, e.classId, u.userName
            ) t
            </script>
            """)
    long countTeacherSubmissionRounds(
            @Param("teacherId") long teacherId,
            @Param("classId") Integer classId,
            @Param("studentNameLike") String studentNameLike);

    @Select("""
            SELECT s.* FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            WHERE eq.experimentId = #{experimentId}
              AND s.studentId = #{studentId}
              AND s.isDelete = 0
              AND s.status IN (0, 1, 2, 3)
            ORDER BY s.submitTime DESC, s.id DESC
            """)
    List<Submission> findConfirmedByExperimentAndStudent(
            @Param("experimentId") long experimentId, @Param("studentId") long studentId);

    @org.apache.ibatis.annotations.Update("""
            UPDATE submission
               SET status = 1, version = version + 1
             WHERE id = #{id}
               AND status = 0
               AND version = #{version}
               AND isDelete = 0
            """)
    int claimPending(@Param("id") long id, @Param("version") int version);

    @org.apache.ibatis.annotations.Update("""
            UPDATE submission
               SET status = 0, version = version + 1
             WHERE id = #{id}
               AND status IN (2, 3)
               AND isDelete = 0
            """)
    int resetForRegrade(@Param("id") long id);

    @Select("""
            <script>
            SELECT s.studentId,
                   s.submitTime,
                   COUNT(*) AS questionCount,
                   SUM(CASE WHEN s.status = 1 THEN 1 ELSE 0 END) AS gradingCount,
                   SUM(CASE WHEN s.status = 2 THEN 1 ELSE 0 END) AS doneCount,
                   SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END) AS failedCount,
                   SUM(s.score) AS scoreSum
            FROM submission s
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            WHERE eq.experimentId = #{experimentId}
              AND s.studentId IN
              <foreach collection="studentIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
              AND s.isDelete = 0
              AND s.status IN (0, 1, 2, 3)
              AND e.lastPublishedTime IS NOT NULL
              AND s.submitTime >= e.lastPublishedTime
            GROUP BY s.studentId, s.submitTime
            </script>
            """)
    List<SubmissionRoundAggregateRow> findCurrentRoundAggregates(
            @Param("experimentId") long experimentId,
            @Param("studentIds") Collection<Long> studentIds);
}
