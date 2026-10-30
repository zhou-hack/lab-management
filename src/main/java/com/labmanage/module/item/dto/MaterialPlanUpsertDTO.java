package com.labmanage.module.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MaterialPlanUpsertDTO(
        @NotBlank(message = "方案名称不能为空") @Size(max = 128, message = "方案名称长度不能超过128") String planName,
        @NotNull(message = "启用状态不能为空") @Min(value = 0, message = "启用状态仅支持0/1")
        @Max(value = 1, message = "启用状态仅支持0/1") Integer enabled,
        @Size(max = 512, message = "备注长度不能超过512") String remark,
        @NotEmpty(message = "方案物料行不能为空") List<@NotNull(message = "方案物料行不能为空") @Valid MaterialPlanLineDTO> lines
) {
}
