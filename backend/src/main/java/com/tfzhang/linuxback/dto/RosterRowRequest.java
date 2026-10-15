package com.tfzhang.linuxback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RosterRowRequest {

    @NotNull(message = "{validation.rowIndex.min}")
    @Min(value = 1, message = "{validation.rowIndex.min}")
    private Integer rowIndex;

    private String studentId;
    private String studentName;
    private String major;
    private String className;
    private String semester;
    private String initialPassword;
}
