package com.tfzhang.linuxback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RosterCreateRequest {

    private String studentId;
    private String studentName;
    private String major;
    private String className;
    private String semester;
    private String initialPassword;
}
