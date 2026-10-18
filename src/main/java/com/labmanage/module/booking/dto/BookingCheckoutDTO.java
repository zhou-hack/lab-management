package com.labmanage.module.booking.dto;

import jakarta.validation.constraints.Size;

/**
 * 结束清点入参（管理员在使用结束后登记清点备注，选填）。
 */
public record BookingCheckoutDTO(

        @Size(max = 512, message = "清点备注长度不能超过 512")
        String note
) {
}
