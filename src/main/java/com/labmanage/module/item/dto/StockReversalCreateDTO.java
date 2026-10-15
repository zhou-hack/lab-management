package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 冲销单登记 DTO（REVERSAL 整单冲销一次；行由服务端从原单复制，写相反流水）
 */
@Schema(description = "冲销单登记请求")
public record StockReversalCreateDTO(
        @Schema(description = "冲销原单 ID（仅允许已过账且未产生归还/核销/申领依赖的单据）", example = "1234567890123456789")
        @NotBlank(message = "冲销原单不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "冲销原单 ID 格式无效")
        String reversalOfId,

        @Schema(description = "冲销原因（必填）", example = "入库登记数量录错")
        @NotBlank(message = "冲销原因不能为空")
        @Size(max = 2000, message = "冲销原因不能超过 2000 个字符")
        String reason
) {
}
