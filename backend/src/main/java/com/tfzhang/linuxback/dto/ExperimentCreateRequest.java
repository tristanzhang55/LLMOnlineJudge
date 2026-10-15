package com.tfzhang.linuxback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExperimentCreateRequest {

    @NotBlank(message = "{validation.title.required}")
    @Size(max = 512, message = "{validation.title.max}")
    private String title;

    private String description;

    @NotNull(message = "{validation.classId.required}")
    @Min(value = 1, message = "{validation.classId.min}")
    private Long classId;

    @NotNull(message = "{validation.deadlineTime.required}")
    private LocalDateTime deadlineTime;

    @NotEmpty(message = "{validation.questions.notEmpty}")
    @Size(max = 100, message = "{validation.questions.max}")
    @Valid
    private List<ExperimentQuestionCreateRequest> questions;
}
