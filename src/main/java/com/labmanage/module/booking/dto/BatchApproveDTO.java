package com.labmanage.module.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量审批请求（单次预约，≤100 条；逐单独立审批，互不影响）。
 */
public record BatchApproveDTO(
        @NotEmpty(message = "审批单号列表不能为空")
        @Size(max = 100, message = "单次批量审批不能超过 100 条")
        List<@NotNull(message = "审批单号不能为空") Long> ids
) {
}
