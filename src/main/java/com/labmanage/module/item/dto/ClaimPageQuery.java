package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record ClaimPageQuery(
        @Min(value = 1, message = "pageNum 最小为 1") Integer pageNum,
        @Min(value = 1, message = "pageSize 最小为 1") @Max(value = 100, message = "pageSize 最大为 100") Integer pageSize,
        @Size(max = 32, message = "状态参数过长") String status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookingDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookingDateTo,
        @Pattern(regexp = "^[1-9]\\d*$", message = "申请人ID不合法") String applicantId,
        Boolean managedOnly,
        @Size(max = 32, message = "排序字段过长") String sortBy,
        @Pattern(regexp = "(?i)asc|desc", message = "排序方向仅支持asc/desc") String sortOrder
) {
    public ClaimPageQuery {
        if (pageNum == null) {
            pageNum = 1;
        }
        if (pageSize == null) {
            pageSize = 20;
        }
    }
}
