package com.labmanage.module.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import com.labmanage.module.item.validation.IntegerQuantity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record TransferCreateDTO(
        @NotBlank(message = "源库位不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "源库位ID不合法") String fromLocationId,
        @NotBlank(message = "目标库位不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "目标库位ID不合法") String toLocationId,
        @NotEmpty(message = "移动行不能为空") List<@NotNull(message = "移动行不能为空") @Valid Line> lines,
        @Size(max = 128, message = "幂等键长度不能超过128") String idempotencyKey,
        @Size(max = 512, message = "备注长度不能超过512") String remark
) {
    public record Line(
            @Pattern(regexp = "^[1-9]\\d*$", message = "物料ID不合法") String materialId,
            @Pattern(regexp = "^[1-9]\\d*$", message = "固定资产ID不合法") String assetId,
            @DecimalMin(value = "1", message = "移动数量必须大于0")
            @IntegerQuantity(message = "移动数量必须为整数，整数位数不能超过15位") BigDecimal quantity,
            @Size(max = 256, message = "库内位置长度不能超过256") String positionText
    ) {
    }
}
