package com.tfzhang.linuxback.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
@ColumnWidth(16)
public class GradeExcelRow {

    @ExcelProperty(value = "学号", index = 0)
    private String studentId;

    @ExcelProperty(value = "姓名", index = 1)
    private String studentName;

    @ExcelProperty(value = "专业", index = 2)
    private String major;

    @ExcelProperty(value = "成绩", index = 3)
    private Integer score;

    @ExcelProperty(value = "总分", index = 4)
    private Integer totalScore;

    @ExcelProperty(value = "状态", index = 5)
    private String statusLabel;
}
