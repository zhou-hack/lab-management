package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 设备使用申请提交请求（一台一单；学生须指定审批教师，教师本人申请可免）
 */
public record BookingApplyRequest(
        @NotNull(message = "设备不能为空")
        String assetId,

        @NotBlank(message = "借用用途不能为空")
        @Size(max = 500, message = "借用用途长度不能超过 500")
        String purpose,

        @NotNull(message = "开始时间不能为空")
        Instant startAt,

        @NotNull(message = "结束时间不能为空")
        Instant endAt,

        /** 指定审批教师（学生单必填，教师单可空） */
        String approvalTeacherId
) {
}
