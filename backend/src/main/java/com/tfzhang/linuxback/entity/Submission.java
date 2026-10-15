package com.tfzhang.linuxback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("submission")
public class Submission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long experimentQuestionId;
    private Long studentId;
    private String answerBlocks;
    private Integer status;
    private Integer aiScore;
    private Integer score;
    private String aiEvidence;
    private Integer isManualOverride;
    private Integer version;
    private LocalDateTime submitTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
