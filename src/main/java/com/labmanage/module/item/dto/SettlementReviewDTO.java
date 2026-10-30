package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SettlementReviewDTO(
        @NotNull(message = "复核结论不能为空") Boolean approve,
        @NotNull(message = "单据版本不能为空") @Min(value = 0, message = "单据版本不能小于0") Integer version,
        @Size(max = 512, message = "复核意见长度不能超过512") String reason
) {
}
