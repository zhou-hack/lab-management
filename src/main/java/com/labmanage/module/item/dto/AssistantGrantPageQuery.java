package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AssistantGrantPageQuery(
        @Min(value = 1, message = "pageNum 最小为 1") Integer pageNum,
        @Min(value = 1, message = "pageSize 最小为 1") @Max(value = 100, message = "pageSize 最大为 100") Integer pageSize,
        @Size(max = 16, message = "授权状态参数过长") String status
) {
    public AssistantGrantPageQuery {
        if (pageNum == null) {
            pageNum = 1;
        }
        if (pageSize == null) {
            pageSize = 20;
        }
    }
}
