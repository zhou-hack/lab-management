package com.labmanage.module.equipment.dto;

import com.labmanage.module.equipment.enums.ReturnCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 归还登记请求（完好恢复空闲；损坏必填备注并转入维护中）
 */
public record BookingReturnRequest(
        @NotNull(message = "归还状态不能为空")
        ReturnCondition condition,

        @Size(max = 500, message = "归还备注长度不能超过 500")
        String note
) {
}
