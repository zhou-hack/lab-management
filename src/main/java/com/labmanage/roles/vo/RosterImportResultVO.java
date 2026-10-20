package com.labmanage.roles.vo;

import java.util.List;

/**
 * 名单批量导入结果（部分成功语义：成功行已入库，失败行给出行级原因）
 *
 * @param errors 失败明细，最多返回前 50 条
 */
public record RosterImportResultVO(
        int totalCount,
        int successCount,
        int failCount,
        List<RowError> errors
) {

    /**
     * 行级失败明细
     *
     * @param rowIndex 物理行号（含表头，与 Excel 行号一致）
     */
    public record RowError(int rowIndex, String personNo, String reason) {
    }
}
