package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.Size;

/**
 * 借用单撤销请求（仅 PENDING 态申请人本人可撤销）
 */
public record BookingCancelRequest(
        @Size(max = 500, message = "撤销原因长度不能超过 500")
        String reason
) {
}
