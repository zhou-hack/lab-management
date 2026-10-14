package com.labmanage.module.booking.dto;

import jakarta.validation.constraints.Size;

/**
 * 预约取消入参（一期仅申请人本人取消，取消类型固定 MANUAL）。
 */
public record BookingCancelDTO(

        @Size(max = 512, message = "取消原因长度不能超过 512")
        String reason
) {
}
