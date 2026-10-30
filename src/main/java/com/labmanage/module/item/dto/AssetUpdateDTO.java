package com.labmanage.module.item.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AssetUpdateDTO(
        @NotBlank(message = "资产编码不能为空") @Size(max = 64, message = "资产编码长度不能超过64") String assetCode,
        @DecimalMin(value = "0.00", message = "资产单价不能为负数")
        @Digits(integer = 16, fraction = 2, message = "资产单价最多16位整数和2位小数") BigDecimal price,
        @Size(max = 256, message = "库内位置长度不能超过256") String positionText
) {
}
