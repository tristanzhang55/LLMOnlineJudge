package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentQuestionVO {

    private Long id;
    private Long experimentId;
    private Long questionId;
    private String questionTitle;
    private Integer questionTotalScore;
    private Integer subQuestionCount;
    private Integer points;
    private Integer effectivePoints;
    private Integer sortOrder;
    private Boolean questionDeleted;
    private Boolean hasSubmissions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDelete;
}
