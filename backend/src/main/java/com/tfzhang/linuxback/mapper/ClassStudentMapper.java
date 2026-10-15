package com.tfzhang.linuxback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tfzhang.linuxback.dto.GradeRosterRow;
import com.tfzhang.linuxback.entity.ClassStudent;
import com.tfzhang.linuxback.vo.RosterStudentRowVO;
import com.tfzhang.linuxback.vo.UserEnrollmentVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ClassStudentMapper extends BaseMapper<ClassStudent> {

    @Select("SELECT id, classId, studentId, joinTime, isDelete FROM class_student WHERE id = #{id}")
    ClassStudent findByIdIncludingDeleted(@Param("id") long id);

    @Select("""
            <script>
            SELECT cs.id          AS classStudentId,
                   u.userAccount  AS studentId,
                   u.userName     AS studentName,
                   u.major        AS major,
                   c.id           AS classId,
                   c.name         AS className,
                   c.semester     AS semester,
                   cs.joinTime    AS joinTime
            FROM class_student cs
            INNER JOIN `class` c ON c.id = cs.classId AND c.isDelete = 0
            INNER JOIN `user`  u ON u.id = cs.studentId AND u.isDelete = 0
            WHERE cs.isDelete = 0
              AND c.teacherId = #{teacherId}
              <if test="classId != null">
                AND c.id = #{classId}
              </if>
              <if test="semester != null">
                AND c.semester = #{semester}
              </if>
              <if test="studentNameLike != null">
                AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
              </if>
            ORDER BY cs.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RosterStudentRowVO> findTeacherRoster(
            @Param("teacherId") long teacherId,
            @Param("classId") Long classId,
            @Param("semester") String semester,
            @Param("studentNameLike") String studentNameLike,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM class_student cs
            INNER JOIN `class` c ON c.id = cs.classId AND c.isDelete = 0
            INNER JOIN `user`  u ON u.id = cs.studentId AND u.isDelete = 0
            WHERE cs.isDelete = 0
              AND c.teacherId = #{teacherId}
              <if test="classId != null">
                AND c.id = #{classId}
              </if>
              <if test="semester != null">
                AND c.semester = #{semester}
              </if>
              <if test="studentNameLike != null">
                AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
              </if>
            </script>
            """)
    long countTeacherRoster(
            @Param("teacherId") long teacherId,
            @Param("classId") Long classId,
            @Param("semester") String semester,
            @Param("studentNameLike") String studentNameLike);

    @Select("""
            <script>
            SELECT cs.id          AS classStudentId,
                   u.id           AS userId,
                   u.userAccount  AS studentId,
                   u.userName     AS studentName,
                   u.major        AS major,
                   c.name         AS className
            FROM class_student cs
            INNER JOIN `class` c ON c.id = cs.classId AND c.isDelete = 0
            INNER JOIN `user`  u ON u.id = cs.studentId AND u.isDelete = 0
            WHERE cs.isDelete = 0
              AND c.id = #{classId}
              AND c.teacherId = #{teacherId}
              <if test="studentNameLike != null">
                AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
              </if>
            ORDER BY cs.id ASC
            <if test="limit != null">
              LIMIT #{limit} OFFSET #{offset}
            </if>
            </script>
            """)
    List<GradeRosterRow> findGradeRoster(
            @Param("teacherId") long teacherId,
            @Param("classId") long classId,
            @Param("studentNameLike") String studentNameLike,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM class_student cs
            INNER JOIN `class` c ON c.id = cs.classId AND c.isDelete = 0
            INNER JOIN `user`  u ON u.id = cs.studentId AND u.isDelete = 0
            WHERE cs.isDelete = 0
              AND c.id = #{classId}
              AND c.teacherId = #{teacherId}
              <if test="studentNameLike != null">
                AND u.userName LIKE #{studentNameLike} ESCAPE '\\\\'
              </if>
            </script>
            """)
    long countGradeRoster(
            @Param("teacherId") long teacherId,
            @Param("classId") long classId,
            @Param("studentNameLike") String studentNameLike);

    @Select("""
            SELECT c.id        AS classId,
                   c.name      AS className,
                   c.semester  AS semester,
                   cs.joinTime AS joinTime
            FROM class_student cs
            INNER JOIN `class` c ON c.id = cs.classId AND c.isDelete = 0
            INNER JOIN `user`  u ON u.id = cs.studentId AND u.isDelete = 0
            WHERE cs.isDelete = 0
              AND cs.studentId = #{userId}
            ORDER BY cs.id ASC
            """)
    List<UserEnrollmentVO> findStudentEnrollments(@Param("userId") long userId);
}
