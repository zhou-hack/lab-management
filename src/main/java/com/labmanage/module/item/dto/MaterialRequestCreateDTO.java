package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 物料申领单创建 DTO（登记草稿；定额快照自动计算，超定额进入签批标记）
 */
@Schema(description = "物料申领单创建参数")
public record MaterialRequestCreateDTO(
        @Schema(description = "领用实验室 ID", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "实验室 ID 格式无效")
        String labId,

        @Schema(description = "归属老师 ID", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "归属老师 ID 格式无效")
        String ownerTeacherId,

        @Schema(description = "经办人 ID（助理代领时为助理本人）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "经办人 ID 格式无效")
        String operatorId,

        @Schema(description = "命中的助理授权 ID", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "助理授权 ID 格式无效")
        String grantId,

        @Schema(description = "开课实例 ID（定额快照来源）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "开课实例 ID 格式无效")
        String offeringId,

        @Schema(description = "场地预约 ID（与课次不能同时填）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "场地预约 ID 格式无效")
        String labBookingId,

        @Schema(description = "课次 ID（与预约不能同时填）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "课次 ID 格式无效")
        String scheduleOccurrenceId,

        @Schema(description = "参与人数（非负）", example = "30")
        @Min(value = 0, message = "参与人数不能为负")
        Integer participantCount,

        @Schema(description = "课时数（非负）", example = "4")
        @Min(value = 0, message = "课时数不能为负")
        Integer periodCount,

        @Schema(description = "用途", example = "有机化学实验课耗材")
        @Size(max = 512, message = "用途不超过 512 字")
        String purpose,

        @Schema(description = "申领行")
        @NotEmpty(message = "申领行不能为空")
        @Valid
        List<MaterialRequestLineDTO> lines
) {
}
