package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 申领单归还 DTO（生成绑定原出库行的 RETURN_IN 归还单草稿，审批过账后回增库存）
 */
@Schema(description = "申领单归还请求")
public record MaterialRequestReturnDTO(
        @Schema(description = "归还时间（带偏移量 ISO 8601；缺省取服务端当前 UTC 时间）",
                example = "2026-10-24T17:00:00+08:00")
        OffsetDateTime occurredAt,

        @Schema(description = "事由（数量差异必填原因）", example = "课后归还剩余连接线")
        @Size(max = 2000, message = "事由不能超过 2000 个字符")
        String reason,

        @Schema(description = "归还明细行（按原出库行归还，可多次部分归还）")
        @NotEmpty(message = "归还明细行不能为空")
        List<@Valid ReturnLineDTO> lines
) {
    /**
     * 归还行：绑定原出库行
     */
    @Schema(description = "归还行")
    public record ReturnLineDTO(
            @Schema(description = "原出库行 ID（取自 returnable_lines 接口）", example = "1234567890123456789")
            @NotBlank(message = "原出库行不能为空")
            @Pattern(regexp = "^\\d{1,19}$", message = "原出库行 ID 格式无效")
            String issueLineId,

            @Schema(description = "本次归还量（不超过可归还量）", example = "3")
            @NotNull(message = "归还数量不能为空")
            @DecimalMin(value = "0", inclusive = false, message = "归还数量必须大于 0")
            @Digits(integer = 15, fraction = 3, message = "归还数量整数最多 15 位、小数最多 3 位")
            BigDecimal quantity,

            @Schema(description = "归还入库位 ID（可空，默认回原出库位）", example = "1234567890123456789")
            @Pattern(regexp = "^\\d{1,19}$", message = "归还入库位 ID 格式无效")
            String toLocationId
    ) {
    }
}
