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

public record TransferWriteoffCreateDTO(
        @NotEmpty(message = "核销行不能为空") List<@NotNull(message = "核销行不能为空") @Valid Line> lines,
        @Size(max = 128, message = "幂等键长度不能超过128") String idempotencyKey,
        @Size(max = 512, message = "备注长度不能超过512") String remark
) {
    public record Line(
            @NotBlank(message = "来源移动行不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "来源移动行ID不合法") String sourceMaterialDocumentLineId,
            @NotBlank(message = "核销类型不能为空") @Pattern(regexp = "LOST|SCRAP", message = "在途核销仅支持LOST/SCRAP") String settlementType,
            @NotNull(message = "核销数量不能为空") @DecimalMin(value = "1", message = "核销数量必须大于0")
            @IntegerQuantity(message = "核销数量必须为整数，整数位数不能超过15位") BigDecimal quantity,
            @NotBlank(message = "核销原因不能为空") @Size(max = 512, message = "核销原因长度不能超过512") String reason
    ) {
    }
}
