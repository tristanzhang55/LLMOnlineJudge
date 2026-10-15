package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubQuestionVO {

    private Long id;
    private Long questionId;
    private Integer orderIndex;
    private String content;
    private String answerFormat;
    private String language;
    private String referenceAnswer;
    private String rubric;
    private Boolean allowSandbox;
    private Integer score;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDelete;
}
