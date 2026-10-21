package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备台账新增请求（新建设备初始状态 IDLE；价值分级由定级接口登记）
 */
public record AssetCreateRequest(
        @NotNull(message = "设备型号不能为空")
        Long modelId,

        @NotBlank(message = "资产编号不能为空")
        @Size(max = 64, message = "资产编号长度不能超过 64")
        String assetCode,

        @Size(max = 128, message = "序列号长度不能超过 128")
        String serialNo,

        @NotNull(message = "归口部门不能为空")
        Long departmentId,

        @Size(max = 255, message = "存放位置长度不能超过 255")
        String locationDetail,

        Long custodianId,

        LocalDate purchaseDate,

        BigDecimal purchasePrice,

        LocalDate warrantyUntil
) {
}
