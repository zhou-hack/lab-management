package com.labmanage.module.equipment.dto;

import com.labmanage.module.equipment.enums.ReturnCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 借用单归还确认请求（归还异常/DAMAGED 时备注必填，设备转维护中）
 */
public record BookingReturnRequest(
        @NotNull(message = "归还状态不能为空")
        ReturnCondition condition,

        @Size(max = 500, message = "归还备注长度不能超过 500")
        String note
) {
}
