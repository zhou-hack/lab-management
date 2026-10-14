package com.labmanage.module.space.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 实验室新增请求
 */
public record LabCreateRequest(
        @NotNull(message = "所在楼栋不能为空")
        Long buildingId,

        @NotNull(message = "归口部门不能为空")
        Long departmentId,

        @NotBlank(message = "实验室编码不能为空")
        @Size(max = 64, message = "实验室编码长度不能超过 64")
        String labCode,

        @NotBlank(message = "实验室名称不能为空")
        @Size(max = 128, message = "实验室名称长度不能超过 128")
        String labName,

        @NotBlank(message = "实验室类型不能为空")
        @Size(max = 32, message = "实验室类型长度不能超过 32")
        String labType,

        @NotBlank(message = "楼层号不能为空")
        @Size(max = 16, message = "楼层号长度不能超过 16")
        String floorNo,

        @NotBlank(message = "房间号不能为空")
        @Size(max = 64, message = "房间号长度不能超过 64")
        String roomNo,

        @NotNull(message = "容纳人数不能为空")
        @Min(value = 1, message = "容纳人数必须大于 0")
        Integer capacity,

        BigDecimal areaSqm,

        String locationNote
) {
}
