package com.labmanage.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页返回体
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResult<T>(
        List<T> records,
        Long total,
        Integer pageNum,
        Integer pageSize
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static <T> PageResult<T> of(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }
}
