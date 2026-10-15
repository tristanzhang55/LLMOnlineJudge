package com.tfzhang.linuxback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuestionUpdateRequest {

    @NotBlank(message = "{validation.title.required}")
    @Size(max = 512, message = "{validation.title.max}")
    private String title;

    @NotBlank(message = "{validation.content.required}")
    private String content;

    @NotBlank(message = "{validation.type.required}")
    @Pattern(regexp = "shell_script|c_program|qa",
            message = "{validation.type.enum}")
    private String type;

    @NotBlank(message = "{validation.difficulty.required}")
    @Pattern(regexp = "easy|medium|hard",
            message = "{validation.difficulty.enum}")
    private String difficulty;

    @Size(max = 1024, message = "{validation.tags.max}")
    private String tags;

    @NotEmpty(message = "{validation.subQuestions.notEmpty}")
    @Size(max = 50, message = "{validation.subQuestions.max}")
    @Valid
    private List<SubQuestionUpdateRequest> subQuestions;
}
