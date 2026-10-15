package com.tfzhang.linuxback.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeListResponse {

    private List<GradeStudentRowVO> data = new ArrayList<>();
    private long total;
    private boolean experimentSelected;
}
