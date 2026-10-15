package com.tfzhang.linuxback.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> data;
    private long total;

    public static <T> PageResult<T> of(List<T> data) {
        return new PageResult<>(data, data.size());
    }

    public static <T> PageResult<T> of(List<T> data, long total) {
        return new PageResult<>(data, total);
    }
}
