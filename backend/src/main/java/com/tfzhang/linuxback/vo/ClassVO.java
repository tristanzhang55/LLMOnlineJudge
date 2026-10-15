package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClassVO {

    private Long id;
    private String name;
    private String semester;
    private LocalDateTime createTime;
}
