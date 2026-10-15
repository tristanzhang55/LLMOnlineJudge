package com.tfzhang.linuxback.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudentSubmitAnswerRequest {

    @NotNull(message = "{validation.experimentQuestionId.required}")
    private Long experimentQuestionId;

    private JsonNode answerBlocks;
}
