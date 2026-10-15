package com.tfzhang.linuxback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class DisputeForwardRequest {

    @NotBlank(message = "{validation.note.required}")
    private String note;

    @NotEmpty(message = "{validation.disputeIds.size}")
    @Size(min = 1, max = 50, message = "{validation.disputeIds.size}")
    private List<@NotNull(message = "{validation.notNull}") @Min(value = 1, message = "{validation.disputeId.min}") Long> disputeIds;

    public void setNote(String note) {
        this.note = note == null ? null : note.trim();
    }
}
