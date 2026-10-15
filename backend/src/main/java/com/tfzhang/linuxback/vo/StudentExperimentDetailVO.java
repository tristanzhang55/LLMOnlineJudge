package com.tfzhang.linuxback.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentExperimentDetailVO {

    private Long id;
    private String title;
    private String description;
    private Long classId;
    private Integer status;
    private LocalDateTime deadlineTime;
    private LocalDateTime lastPublishedTime;
    private Boolean locked;
    private Boolean resubmissionEligible;
    private List<StudentExperimentQuestionVO> questions;
    private List<StudentSubmissionHistoryRoundVO> history;
}
