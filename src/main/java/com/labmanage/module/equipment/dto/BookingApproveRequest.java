package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 借用单审批请求（老师/管理员共用；驳回时意见必填由服务端校验）
 */
public record BookingApproveRequest(
        /** true=通过 false=驳回 */
        @NotNull(message = "审批结论不能为空")
        Boolean approved,

        @Size(max = 500, message = "审批意见长度不能超过 500")
        String comment
) {
}
