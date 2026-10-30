package com.labmanage.module.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ClaimUpdateDTO(
        @NotBlank(message = "用途不能为空") @Size(max = 512, message = "用途长度不能超过512") String purpose,
        @NotNull(message = "预约日期不能为空") LocalDate bookingDate,
        @NotBlank(message = "起始节次不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "起始节次ID不合法") String startPeriodId,
        @NotBlank(message = "结束节次不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "结束节次ID不合法") String endPeriodId,
        @NotEmpty(message = "申领物料行不能为空") List<@NotNull(message = "申领物料行不能为空") @Valid ClaimPreviewLineDTO> lines,
        List<@NotNull(message = "计划分配不能为空") @Valid ClaimPreviewAllocationDTO> allocations,
        @NotNull(message = "版本号不能为空") @Min(value = 1, message = "版本号不合法") Integer revisionNo
) {
}
