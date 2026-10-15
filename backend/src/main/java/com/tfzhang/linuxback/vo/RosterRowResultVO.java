package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RosterRowResultVO {

    private Integer rowIndex;
    private String studentId;
    private String result;
    private Long userId;
    private Long classId;
    private List<String> messages = new ArrayList<>();
}
