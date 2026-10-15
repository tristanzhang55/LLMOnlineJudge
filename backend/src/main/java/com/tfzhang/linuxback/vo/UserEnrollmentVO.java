package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEnrollmentVO {

    private Long classId;
    private String className;
    private String semester;
    private LocalDateTime joinTime;
}
