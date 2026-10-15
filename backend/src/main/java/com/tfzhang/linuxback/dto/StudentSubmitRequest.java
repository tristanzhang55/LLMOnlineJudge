package com.tfzhang.linuxback.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class StudentSubmitRequest {

    @NotEmpty(message = "{validation.answers.notEmpty}")
    @Valid
    private List<StudentSubmitAnswerRequest> answers;
}
