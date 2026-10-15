package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentExperimentListItem {

    private Long id;
    private String title;
    private String description;
    private Long classId;
    private Integer status;
    private LocalDateTime deadlineTime;
    private LocalDateTime lastPublishedTime;
    private Boolean locked;
    private Boolean resubmissionEligible;
}
