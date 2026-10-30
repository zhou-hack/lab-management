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

public record ClaimIssueCreateDTO(
        @NotBlank(message = "实际库位不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "库位ID不合法") String locationId,
        @Size(max = 128, message = "幂等键长度不能超过128") String idempotencyKey,
        @Size(max = 512, message = "备注长度不能超过512") String remark,
        @NotEmpty(message = "实发行不能为空") List<@NotNull(message = "实发行不能为空") @Valid Line> lines
) {
    public record Line(
            @NotBlank(message = "申领行不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "申领行ID不合法") String claimLineId,
            @Pattern(regexp = "^[1-9]\\d*$", message = "固定资产ID不合法") String fixedAssetId,
            @NotNull(message = "实发数量不能为空") @DecimalMin(value = "1", message = "实发数量必须大于0")
            @IntegerQuantity(message = "实发数量必须为整数，整数位数不能超过15位") BigDecimal quantity,
            @Size(max = 256, message = "库内位置长度不能超过256") String positionText
    ) {
    }
}
