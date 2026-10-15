package com.tfzhang.linuxback.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class RosterExcelReadListener implements ReadListener<RosterExcelRow> {

    private final List<ParsedRosterExcelRow> rows = new ArrayList<>();

    @Override
    public void invoke(RosterExcelRow data, AnalysisContext context) {
        if (isAllBlank(data)) {
            return;
        }
        int excelRowNumber = context.readRowHolder().getRowIndex() + 1;
        rows.add(new ParsedRosterExcelRow(excelRowNumber, data));
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // no-op: do not log rows (initialPassword must not leak)
    }

    static boolean isAllBlank(RosterExcelRow data) {
        if (data == null) {
            return true;
        }
        return isBlank(data.getStudentId())
                && isBlank(data.getStudentName())
                && isBlank(data.getMajor())
                && isBlank(data.getClassName())
                && isBlank(data.getSemester())
                && isBlank(data.getInitialPassword());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record ParsedRosterExcelRow(int rowIndex, RosterExcelRow data) {
    }
}
