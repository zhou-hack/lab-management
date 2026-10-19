package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.Size;

/**
 * 撤销本人申请请求（借出前可撤销，原因留痕）
 */
public record BookingCancelRequest(
        @Size(max = 500, message = "撤销原因长度不能超过 500")
        String reason
) {
}
