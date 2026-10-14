package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.Size;

/**
 * 借用单借出确认请求（须在借用时间窗口内）
 */
public record BookingCheckoutRequest(
        @Size(max = 500, message = "借出备注长度不能超过 500")
        String note
) {
}
