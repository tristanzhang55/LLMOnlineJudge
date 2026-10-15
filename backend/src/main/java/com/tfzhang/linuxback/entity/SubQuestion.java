package com.tfzhang.linuxback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sub_question")
public class SubQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;
    private Integer orderIndex;
    private String content;
    private String answerFormat;
    private String language;
    private String referenceAnswer;
    private String rubric;
    private Integer allowSandbox;
    private Integer score;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
