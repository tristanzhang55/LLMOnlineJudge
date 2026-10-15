package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RosterPreviewRowVO {

    private Integer rowIndex;
    private String studentId;
    private String studentName;
    private String major;
    private String className;
    private String semester;
    private String initialPassword;
    private String status;
    private List<String> messages = new ArrayList<>();
}
