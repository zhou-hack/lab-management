package com.labmanage.module.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 预约驳回入参（驳回原因必填，回显给申请人）。
 */
public record BookingRejectDTO(

        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 512, message = "驳回原因长度不能超过 512")
        String reason
) {
}
