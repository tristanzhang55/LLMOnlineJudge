package com.tfzhang.linuxback.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmissionDraftRequest {

    @NotNull(message = "{validation.experimentQuestionId.required}")
    @Min(value = 1, message = "{validation.experimentQuestionId.min}")
    private Long experimentQuestionId;

    private JsonNode answerBlocks;
}
