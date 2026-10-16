package com.labmanage.module.space.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 实验室创建请求
 */
public record LabCreateDTO(

        @NotNull(message = "所属楼栋不能为空")
        Long buildingId,

        @NotNull(message = "管理部门不能为空")
        Long departmentId,

        @NotBlank(message = "实验室编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "实验室编码需为 1-64 位字母、数字或下划线")
        String labCode,

        @NotBlank(message = "实验室名称不能为空")
        @Size(max = 128, message = "实验室名称长度不能超过 128")
        String labName,

        @NotBlank(message = "实验室类型不能为空")
        @Pattern(regexp = "TEACHING|RESEARCH|OPEN|COMPREHENSIVE", message = "实验室类型需为 TEACHING/RESEARCH/OPEN/COMPREHENSIVE")
        String labType,

        @NotBlank(message = "楼层不能为空")
        @Size(max = 16, message = "楼层长度不能超过 16")
        String floorNo,

        @NotBlank(message = "房间号不能为空")
        @Size(max = 64, message = "房间号长度不能超过 64")
        String roomNo,

        @NotNull(message = "容纳人数不能为空")
        @Min(value = 1, message = "容纳人数须大于 0")
        @Max(value = 100000, message = "容纳人数不能超过 100000")
        Integer capacity,

        @DecimalMin(value = "0.01", message = "建筑面积须大于 0")
        BigDecimal areaSqm,

        @Size(max = 2000, message = "位置说明长度不能超过 2000")
        String locationNote
) {
}
