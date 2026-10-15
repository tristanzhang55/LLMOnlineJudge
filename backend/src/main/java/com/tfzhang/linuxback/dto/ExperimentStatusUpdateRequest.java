package com.tfzhang.linuxback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExperimentStatusUpdateRequest {

    @NotNull(message = "{validation.status.required}")
    @Min(value = 0, message = "{validation.status.range}")
    @Max(value = 2, message = "{validation.status.range}")
    private Integer status;
}
