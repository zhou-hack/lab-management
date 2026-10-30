package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockBalancePositionUpdateDTO(
        @Size(max = 256, message = "库内位置长度不能超过256") String positionText,
        @NotNull(message = "库存版本号不能为空") @Min(value = 0, message = "库存版本号不能为负数") Integer version
) {
}
