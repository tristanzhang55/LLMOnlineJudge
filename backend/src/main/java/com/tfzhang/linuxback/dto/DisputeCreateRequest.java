package com.tfzhang.linuxback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DisputeCreateRequest {

    @NotBlank(message = "{validation.disputeReason.required}")
    @Size(max = 1024, message = "{validation.disputeReason.max}")
    private String disputeReason;

    public void setDisputeReason(String disputeReason) {
        this.disputeReason = disputeReason == null ? null : disputeReason.trim();
    }
}
