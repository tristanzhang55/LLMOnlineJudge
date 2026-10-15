package com.tfzhang.linuxback.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DisputeResolveScoreItem {

    @NotNull(message = "{validation.subQuestionId.required}")
    private Long subQuestionId;

    @NotNull(message = "{validation.manualScore.required}")
    private BigDecimal manualScore;
}
