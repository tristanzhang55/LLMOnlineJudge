package com.tfzhang.linuxback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExperimentQuestionCreateRequest {

    @NotNull(message = "{validation.questionId.required}")
    @Min(value = 1, message = "{validation.questionId.min}")
    private Long questionId;

    @Min(value = 0, message = "{validation.points.range}")
    @Max(value = 10000, message = "{validation.points.range}")
    private Integer points;
}
