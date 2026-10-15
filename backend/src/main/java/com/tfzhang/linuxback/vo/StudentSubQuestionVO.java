package com.tfzhang.linuxback.vo;

import lombok.Data;

@Data
public class StudentSubQuestionVO {

    private Long id;
    private Integer orderIndex;
    private String content;
    private String answerFormat;
    private String language;
    private Integer score;
}
