package com.tfzhang.linuxback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RosterUpdateRequest {

    @NotBlank(message = "{validation.notBlank}")
    private String studentName;

    @NotBlank(message = "{validation.notBlank}")
    private String major;

    @NotNull(message = "{validation.notNull}")
    @Min(value = 1, message = "{validation.min.one}")
    private Long classId;
}
