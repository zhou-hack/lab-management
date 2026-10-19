package com.labmanage.module.equipment.vo;

import java.util.List;

/**
 * 设备台账导入结果
 * <p>
 * 按行报告错误（row 为 Excel 行号，从 1 开始计），有效行正常入库，
 * 无效行跳过并说明原因；不因个别行错误回滚整批。
 */
public record AssetImportResultVO(
        Integer total,
        Integer success,
        Integer failed,
        List<RowError> errors
) {

    /**
     * 行级错误（row 为 Excel 中的行号，如第 2 行数据行）
     */
    public record RowError(
            Integer row,
            String message
    ) {
    }
}
