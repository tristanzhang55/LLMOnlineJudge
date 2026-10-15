package com.tfzhang.linuxback.vo;

import lombok.Data;

@Data
public class MySubQuestionScoreVO {

    private Long subQuestionId;
    private Integer maxScore;
    private Integer effectiveScore;
    private String comment;
}