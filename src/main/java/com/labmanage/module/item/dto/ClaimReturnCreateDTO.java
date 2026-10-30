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

public record ClaimReturnCreateDTO(
        @Size(max = 128, message = "幂等键长度不能超过128") String idempotencyKey,
        @Size(max = 512, message = "备注长度不能超过512") String remark,
        @Size(max = 512, message = "逾期原因长度不能超过512") String overdueReason,
        @NotEmpty(message = "归还处理行不能为空") List<@NotNull(message = "归还处理行不能为空") @Valid Line> lines
) {
    public record Line(
            @NotBlank(message = "来源出库行不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "来源出库行ID不合法") String sourceMaterialDocumentLineId,
            @DecimalMin(value = "0.0", message = "回库数量不能为负数")
            @IntegerQuantity(message = "回库数量必须为整数，整数位数不能超过15位") BigDecimal returnQuantity,
            @DecimalMin(value = "0.0", message = "教学消耗数量不能为负数")
            @IntegerQuantity(message = "教学消耗数量必须为整数，整数位数不能超过15位") BigDecimal consumedQuantity,
            @DecimalMin(value = "0.0", message = "丢失数量不能为负数")
            @IntegerQuantity(message = "丢失数量必须为整数，整数位数不能超过15位") BigDecimal lostQuantity,
            @DecimalMin(value = "0.0", message = "损坏数量不能为负数")
            @IntegerQuantity(message = "损坏数量必须为整数，整数位数不能超过15位") BigDecimal damagedQuantity,
            @Size(max = 512, message = "教学消耗原因长度不能超过512") String consumedReason,
            @Size(max = 512, message = "丢失原因长度不能超过512") String lostReason,
            @Size(max = 512, message = "损坏原因长度不能超过512") String damagedReason,
            @Size(max = 256, message = "库内位置长度不能超过256") String positionText
    ) {
    }
}
