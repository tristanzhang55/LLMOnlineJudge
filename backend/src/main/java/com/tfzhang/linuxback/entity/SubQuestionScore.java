package com.tfzhang.linuxback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sub_question_score")
public class SubQuestionScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;
    private Long subQuestionId;
    private BigDecimal aiScore;
    private BigDecimal manualScore;
    private BigDecimal effectiveScore;
    private Integer usedSandbox;
    private String aiEvidence;
    private String gradingLog;
    private LocalDateTime gradedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
