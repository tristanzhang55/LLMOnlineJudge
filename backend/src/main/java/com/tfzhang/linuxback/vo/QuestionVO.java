package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionVO {

    private Long id;
    private String title;
    private String content;
    private String type;
    private String difficulty;
    private String tags;
    private Integer totalScore;
    private Integer submitNum;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDelete;
    private List<SubQuestionVO> subQuestions = new ArrayList<>();
}
