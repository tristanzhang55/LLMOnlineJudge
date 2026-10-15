package com.tfzhang.linuxback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class DisputeResolveRequest {

    @NotBlank(message = "{validation.manualComment.required}")
    private String manualComment;

    @Valid
    private List<DisputeResolveScoreItem> subQuestionScores;

    public void setManualComment(String manualComment) {
        this.manualComment = manualComment == null ? null : manualComment.trim();
    }

    public List<DisputeResolveScoreItem> getSubQuestionScores() {
        return subQuestionScores == null ? List.of() : subQuestionScores;
    }
}
