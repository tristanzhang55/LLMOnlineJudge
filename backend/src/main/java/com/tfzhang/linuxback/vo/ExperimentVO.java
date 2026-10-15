package com.tfzhang.linuxback.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExperimentVO {

    private Long id;
    private String title;
    private String description;
    private Long classId;
    private Integer status;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime deadlineTime;
    private LocalDateTime lastPublishedTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer submittedStudentCount;
    private Boolean isDelete;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ExperimentQuestionVO> questions;
}
