package com.tfzhang.linuxback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SubQuestionUpdateRequest {

    @Positive(message = "{validation.subQuestionId.positive}")
    private Long id;

    @NotBlank(message = "{validation.content.required}")
    private String content;

    @NotBlank(message = "{validation.answerFormat.required}")
    @Pattern(regexp = "image|text|code",
            message = "{validation.answerFormat.enum}")
    private String answerFormat;

    private String referenceAnswer;
    private String rubric;
    private Boolean allowSandbox;
    private String language;

    @NotNull(message = "{validation.score.required}")
    @Min(value = 0, message = "{validation.score.range}")
    @Max(value = 1000, message = "{validation.score.range}")
    private Integer score;
}
