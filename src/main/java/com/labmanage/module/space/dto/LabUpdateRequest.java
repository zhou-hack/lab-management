package com.labmanage.module.space.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 实验室修改请求（编码/楼栋/房间号不可改；换房间应删除后新建）
 */
public record LabUpdateRequest(
        @NotBlank(message = "实验室名称不能为空")
        @Size(max = 128, message = "实验室名称长度不能超过 128")
        String labName,

        @NotBlank(message = "实验室类型不能为空")
        @Size(max = 32, message = "实验室类型长度不能超过 32")
        String labType,

        @NotBlank(message = "楼层号不能为空")
        @Size(max = 16, message = "楼层号长度不能超过 16")
        String floorNo,

        @NotNull(message = "容纳人数不能为空")
        @Min(value = 1, message = "容纳人数必须大于 0")
        Integer capacity,

        BigDecimal areaSqm,

        String locationNote,

        @NotNull(message = "启用状态不能为空")
        Integer enabled
) {
}
