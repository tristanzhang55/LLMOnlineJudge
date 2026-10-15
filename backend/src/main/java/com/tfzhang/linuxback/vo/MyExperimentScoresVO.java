package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.List;

@Data
public class MyExperimentScoresVO {

    private Long experimentId;
    private Boolean isRoundGradingComplete;
    private List<MyQuestionScoresVO> questions;

}

