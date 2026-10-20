package com.labmanage.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 批量导入结果 VO
 */
@Schema(description = "批量导入结果")
public record ImportResultVO(
        @Schema(description = "总数据行数（不含表头与空行）", example = "10")
        int total,

        @Schema(description = "成功行数", example = "8")
        int successCount,

        @Schema(description = "失败行数", example = "2")
        int failureCount,

        @Schema(description = "失败明细（按行号升序）")
        List<Failure> failures,

        @Schema(description = "生成的单据 ID（仅期初余额导入返回草稿单号，其余导入为空）", example = "1234567890123456789")
        String documentId
) {

    public ImportResultVO(int total, int successCount, int failureCount, List<Failure> failures) {
        this(total, successCount, failureCount, failures, null);
    }

    /**
     * 失败行明细
     */
    @Schema(description = "导入失败行明细")
    public record Failure(
            @Schema(description = "文件物理行号（表头为第 1 行）", example = "3")
            int rowNo,

            @Schema(description = "失败原因", example = "SKU 码已存在，不得复用")
            String reason
    ) {
    }
}
