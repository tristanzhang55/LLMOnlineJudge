package com.tfzhang.linuxback.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
@ColumnWidth(20)
public class RosterExcelRow {

    @ExcelProperty(value = "studentId", index = 0, converter = DigitStringConverter.class)
    private String studentId;

    @ExcelProperty(value = "studentName", index = 1, converter = DigitStringConverter.class)
    private String studentName;

    @ExcelProperty(value = "major", index = 2, converter = DigitStringConverter.class)
    private String major;

    @ExcelProperty(value = "className", index = 3, converter = DigitStringConverter.class)
    private String className;

    @ExcelProperty(value = "semester", index = 4, converter = DigitStringConverter.class)
    private String semester;

    @ExcelProperty(value = "initialPassword", index = 5, converter = DigitStringConverter.class)
    private String initialPassword;
}
