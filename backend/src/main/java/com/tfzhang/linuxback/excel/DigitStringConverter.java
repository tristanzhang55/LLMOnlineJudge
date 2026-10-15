package com.tfzhang.linuxback.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

import java.math.BigDecimal;

public class DigitStringConverter implements Converter<String> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return String.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public String convertToJavaData(
            ReadCellData<?> cellData,
            ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration) {
        if (cellData == null || cellData.getType() == null || cellData.getType() == CellDataTypeEnum.EMPTY) {
            return null;
        }
        if (cellData.getType() == CellDataTypeEnum.NUMBER) {
            BigDecimal number = cellData.getNumberValue();
            if (number == null) {
                return null;
            }
            String plain = number.toPlainString();
            if (plain.contains(".")) {
                plain = plain.replaceAll("\\.0+$", "");
            }
            return plain.isEmpty() ? null : plain;
        }
        String raw = cellData.getStringValue();
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public WriteCellData<?> convertToExcelData(
            String value,
            ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration) {
        return new WriteCellData<>(value == null ? "" : value);
    }
}
