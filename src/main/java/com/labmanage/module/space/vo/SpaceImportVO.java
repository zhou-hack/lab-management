package com.labmanage.module.space.vo;

import java.util.List;
import java.util.Map;

/**
 * 场地导入结果（部分成功语义：合法行落库，非法行逐条给出原因）。
 *
 * @param total        参与校验的数据行数（不含表头与全空行）
 * @param successCount 成功落库条数
 * @param failedCount  校验失败条数
 * @param failures     失败明细（行号按表格实际行号，表头为第 1 行；raw 为原始列值，供下载修正）
 */
public record SpaceImportVO(
        int total,
        int successCount,
        int failedCount,
        List<Failure> failures
) {

    /**
     * 单行导入失败明细。
     *
     * @param rowNum   表格行号（含表头，首条数据为 2）
     * @param labName  场地名称（解析失败可能为空，便于列表定位）
     * @param reason   失败原因（多个原因以「；」连接）
     * @param raw      原始列值（key 为规范中文表头，时间审计列原样保留）
     */
    public record Failure(
            int rowNum,
            String labName,
            String reason,
            Map<String, String> raw
    ) {
    }
}
