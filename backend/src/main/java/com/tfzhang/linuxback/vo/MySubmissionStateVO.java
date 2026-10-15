package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.List;

@Data
public class MySubmissionStateVO {

    private Long id;
    private Integer status;
    private Boolean locked;
    private Boolean resubmissionEligible;
    private Boolean isRoundGradingComplete;
    private Boolean editable;
    private List<MySubmissionQuestionStateVO> questions;
}
