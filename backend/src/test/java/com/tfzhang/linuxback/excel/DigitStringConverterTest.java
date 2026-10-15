package com.tfzhang.linuxback.excel;

import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.ReadCellData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DigitStringConverterTest {

    private final DigitStringConverter converter = new DigitStringConverter();

    @Test
    void numberCell_plainDigitsWithoutScientificNotation() {
        ReadCellData<BigDecimal> cell = new ReadCellData<>(new BigDecimal("2021001234"));
        String value = converter.convertToJavaData(cell, null, null);
        assertEquals("2021001234", value);
    }

    @Test
    void numberCell_stripsTrailingZeroFraction() {
        ReadCellData<BigDecimal> cell = new ReadCellData<>(new BigDecimal("2021001234.0"));
        assertEquals("2021001234", converter.convertToJavaData(cell, null, null));
    }

    @Test
    void stringCell_isTrimmed() {
        ReadCellData<String> cell = new ReadCellData<>("  2021001234  ");
        assertEquals("2021001234", converter.convertToJavaData(cell, null, null));
    }

    @Test
    void emptyCell_isNull() {
        ReadCellData<String> cell = new ReadCellData<>(CellDataTypeEnum.EMPTY);
        assertNull(converter.convertToJavaData(cell, null, null));
    }
}
