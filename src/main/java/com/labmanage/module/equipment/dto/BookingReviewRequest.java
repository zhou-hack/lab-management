package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 审批请求（教师初审 / 管理员终审共用；驳回时意见必填）
 */
public record BookingReviewRequest(
        @NotBlank(message = "审批动作不能为空")
        @Pattern(regexp = "approve|reject", message = "审批动作仅支持 approve / reject")
        String action,

        @Size(max = 500, message = "审批意见长度不能超过 500")
        String comment
) {
}
