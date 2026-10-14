package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.SettlementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 物料核销登记 DTO（CONSUMED 耗材消耗 / LOST 丢失 / DAMAGED 损坏；核销不再扣库存）
 */
@Schema(description = "物料核销登记参数")
public record MaterialSettlementCreateDTO(
        @Schema(description = "原出库行 ID（领用出库过账后产生）", example = "1234567890123456789")
        @NotBlank(message = "原出库行不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "原出库行 ID 格式无效")
        String issueLineId,

        @Schema(description = "核销类型（CONSUMED/LOST/DAMAGED；归还 RETURNED 由归还过账自动生成）",
                example = "CONSUMED")
        @NotNull(message = "核销类型不能为空")
        SettlementType settlementType,

        @Schema(description = "本次核销量", example = "5")
        @NotNull(message = "核销量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "核销量必须大于 0")
        @Digits(integer = 15, fraction = 3, message = "核销量整数最多 15 位、小数最多 3 位")
        BigDecimal quantity,

        @Schema(description = "核销原因", example = "耗材实验中消耗完毕")
        @Size(max = 512, message = "核销原因不超过 512 个字符")
        String reason
) {
}
