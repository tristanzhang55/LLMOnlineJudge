package com.tfzhang.linuxback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tfzhang.linuxback.dto.StudentDisputeListRow;
import com.tfzhang.linuxback.dto.TeacherDisputeInboxRow;
import com.tfzhang.linuxback.entity.Dispute;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface DisputeMapper extends BaseMapper<Dispute> {

    @Select("""
            <script>
            SELECT d.id,
                   d.submissionId,
                   e.id AS experimentId,
                   e.title AS experimentTitle,
                   e.classId,
                   s.studentId,
                   u.userName AS studentName,
                   s.experimentQuestionId,
                   q.title AS questionTitle,
                   s.status AS submissionStatus,
                   s.score,
                   CASE WHEN d.resolveTime IS NULL THEN 1 ELSE 2 END AS disputeStatus,
                   d.disputeReason,
                   d.disputeTime,
                   d.manualComment,
                   d.resolveTime,
                   s.isManualOverride
            FROM dispute d
            INNER JOIN submission s ON s.id = d.submissionId
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            INNER JOIN `user` u ON u.id = s.studentId
            LEFT JOIN question q ON q.id = eq.questionId
            WHERE e.userId = #{teacherId}
              AND s.isDelete = 0
              AND d.isDelete = 0
              AND u.isDelete = 0
              AND d.withdrawnTime IS NULL
              <choose>
                <when test="disputeStatus != null and disputeStatus == 2">
                  AND d.resolveTime IS NOT NULL
                </when>
                <otherwise>
                  AND d.resolveTime IS NULL
                </otherwise>
              </choose>
              <if test="classId != null">
                AND e.classId = #{classId}
              </if>
              <if test="experimentId != null">
                AND e.id = #{experimentId}
              </if>
              <if test="studentNameLike != null">
                AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
              </if>
            ORDER BY d.disputeTime DESC, d.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<TeacherDisputeInboxRow> findTeacherDisputes(
            @Param("teacherId") long teacherId,
            @Param("classId") Integer classId,
            @Param("experimentId") Integer experimentId,
            @Param("studentNameLike") String studentNameLike,
            @Param("disputeStatus") Integer disputeStatus,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM dispute d
            INNER JOIN submission s ON s.id = d.submissionId
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            INNER JOIN `user` u ON u.id = s.studentId
            WHERE e.userId = #{teacherId}
              AND s.isDelete = 0
              AND d.isDelete = 0
              AND u.isDelete = 0
              AND d.withdrawnTime IS NULL
              <choose>
                <when test="disputeStatus != null and disputeStatus == 2">
                  AND d.resolveTime IS NOT NULL
                </when>
                <otherwise>
                  AND d.resolveTime IS NULL
                </otherwise>
              </choose>
              <if test="classId != null">
                AND e.classId = #{classId}
              </if>
              <if test="experimentId != null">
                AND e.id = #{experimentId}
              </if>
              <if test="studentNameLike != null">
                AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
              </if>
            </script>
            """)
    long countTeacherDisputes(
            @Param("teacherId") long teacherId,
            @Param("classId") Integer classId,
            @Param("experimentId") Integer experimentId,
            @Param("studentNameLike") String studentNameLike,
            @Param("disputeStatus") Integer disputeStatus);

    @Select("""
            SELECT d.id,
                   d.submissionId,
                   eq.experimentId,
                   e.title AS experimentTitle,
                   s.experimentQuestionId,
                   q.title AS questionTitle,
                   CASE
                     WHEN d.withdrawnTime IS NOT NULL THEN 3
                     WHEN d.resolveTime IS NOT NULL THEN 2
                     ELSE 1
                   END AS disputeStatus,
                   d.disputeTime,
                   s.status AS submissionStatus,
                   s.score
            FROM dispute d
            INNER JOIN submission s ON s.id = d.submissionId AND s.isDelete = 0
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            INNER JOIN question q ON q.id = eq.questionId
            WHERE d.isDelete = 0
              AND s.studentId = #{studentId}
            ORDER BY d.disputeTime DESC, d.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<StudentDisputeListRow> findStudentDisputes(
            @Param("studentId") long studentId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            SELECT COUNT(*)
            FROM dispute d
            INNER JOIN submission s ON s.id = d.submissionId AND s.isDelete = 0
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            INNER JOIN question q ON q.id = eq.questionId
            WHERE d.isDelete = 0
              AND s.studentId = #{studentId}
            """)
    long countStudentDisputes(@Param("studentId") long studentId);

    @Select("""
            SELECT d.* FROM dispute d
            INNER JOIN submission s ON s.id = d.submissionId AND s.isDelete = 0
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            WHERE eq.experimentId = #{experimentId}
              AND d.isDelete = 0
              AND d.resolveTime IS NULL
              AND d.withdrawnTime IS NULL
            """)
    List<Dispute> findOpenByExperimentId(@Param("experimentId") long experimentId);

    @Update("""
            UPDATE dispute
               SET withdrawnTime = #{withdrawnTime}
             WHERE id = #{id}
               AND resolveTime IS NULL
               AND withdrawnTime IS NULL
               AND isDelete = 0
            """)
    int casWithdraw(@Param("id") long id, @Param("withdrawnTime") LocalDateTime withdrawnTime);

    @Update("""
            UPDATE dispute
               SET manualComment = #{manualComment},
                   resolveTime = #{resolveTime}
             WHERE id = #{id}
               AND resolveTime IS NULL
               AND withdrawnTime IS NULL
               AND isDelete = 0
            """)
    int casResolve(
            @Param("id") long id,
            @Param("manualComment") String manualComment,
            @Param("resolveTime") LocalDateTime resolveTime);

    @Select("""
            SELECT COUNT(*)
            FROM dispute d
            INNER JOIN submission s ON s.id = d.submissionId AND s.isDelete = 0
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
            INNER JOIN experiment e ON e.id = eq.experimentId
            INNER JOIN question q ON q.id = eq.questionId
            WHERE d.isDelete = 0
              AND s.studentId = #{studentId}
              AND d.resolveTime IS NOT NULL
              AND d.withdrawnTime IS NULL
              AND d.replyReadTime IS NULL
              AND (d.manualComment IS NULL
                   OR d.manualComment <> #{autoCloseNote})
            """)
    long countUnreadTeacherReplies(
            @Param("studentId") long studentId,
            @Param("autoCloseNote") String autoCloseNote);

    @Update("""
            UPDATE dispute d
            INNER JOIN submission s ON s.id = d.submissionId AND s.isDelete = 0
            INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
               SET d.replyReadTime = #{now}
             WHERE d.isDelete = 0
               AND s.studentId = #{studentId}
               AND eq.experimentId = #{experimentId}
               AND d.resolveTime IS NOT NULL
               AND d.withdrawnTime IS NULL
               AND d.replyReadTime IS NULL
            """)
    int markRepliesRead(
            @Param("studentId") long studentId,
            @Param("experimentId") long experimentId,
            @Param("now") LocalDateTime now);
}
