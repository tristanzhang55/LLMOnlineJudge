package com.tfzhang.linuxback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tfzhang.linuxback.entity.Experiment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ExperimentMapper extends BaseMapper<Experiment> {

    @Select("""
            SELECT COUNT(DISTINCT e.id)
            FROM experiment e
            INNER JOIN class_student cs
                    ON cs.classId = e.classId
                   AND cs.studentId = #{studentId}
                   AND cs.isDelete = 0
            WHERE e.isDelete = 0
              AND e.status = 1
              AND NOT EXISTS (
                    SELECT 1
                    FROM submission s
                    INNER JOIN experiment_question eq ON eq.id = s.experimentQuestionId
                    INNER JOIN experiment e2 ON e2.id = eq.experimentId
                    WHERE eq.experimentId = e.id
                      AND s.studentId = #{studentId}
                      AND e2.lastPublishedTime IS NOT NULL
                      AND s.submitTime >= e2.lastPublishedTime
                      AND s.isDelete = 0
                  )
            """)
    long countPendingAssignments(@Param("studentId") long studentId);
}
