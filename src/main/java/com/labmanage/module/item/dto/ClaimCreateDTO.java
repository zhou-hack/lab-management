package com.labmanage.module.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ClaimCreateDTO(
        @NotBlank(message = "用途不能为空") @Size(max = 512, message = "用途长度不能超过512") String purpose,
        @NotNull(message = "预约日期不能为空") LocalDate bookingDate,
        @Pattern(regexp = "^[1-9]\\d*$", message = "起始节次ID不合法") String startPeriodId,
        @Pattern(regexp = "^[1-9]\\d*$", message = "结束节次ID不合法") String endPeriodId,
        @NotEmpty(message = "申领物料行不能为空") List<@NotNull(message = "申领物料行不能为空") @Valid ClaimPreviewLineDTO> lines,
        List<@NotNull(message = "计划分配不能为空") @Valid ClaimPreviewAllocationDTO> allocations,
        @Size(max = 128, message = "幂等键长度不能超过128") String idempotencyKey,
        @Size(max = 12, message = "节次最多选择12个") List<@Pattern(regexp = "^[1-9]\\d*$", message = "节次ID不合法") String> periodIds
) {
}
