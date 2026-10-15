package com.tfzhang.linuxback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RosterConfirmRequest {

    @NotEmpty(message = "{validation.rows.size}")
    @Size(min = 1, max = 5000, message = "{validation.rows.size}")
    @Valid
    private List<RosterRowRequest> rows;
}
