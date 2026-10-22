package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;

import java.util.List;

/**
 * 导入模板表头定义与校验
 * <p>
 * 只校验前 N 列逐列相等（trim 后），允许多余尾列——导出文件（模板列在前、附加列在后）可直接回导。
 */
public final class ImportColumns {

    /** 物料导入模板列（前 9 列，导出文件前 9 列与此一致） */
    public static final List<String> SKU_HEADER = List.of(
            "分类编码", "SKU码", "物料名称", "规格型号", "计量单位", "数量精度", "物料类型", "危险品", "高价值");

    /** 分类导入模板列 */
    public static final List<String> CATEGORY_HEADER = List.of("分类编码", "分类名称", "父分类编码");

    /** 期初余额导入模板列（有效行组装为一张 OPENING 期初入库草稿单，走既有审批过账） */
    public static final List<String> BALANCE_OPENING_HEADER = List.of("库位编码", "SKU码", "数量", "单价");

    private ImportColumns() {
    }

    /**
     * 校验表头行前 N 列与模板一致，不匹配抛 40000
     */
    public static void checkHeader(List<String> headerRow, List<String> expected) {
        if (headerRow == null || headerRow.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "文件为空，请下载最新模板填写后导入");
        }
        int n = expected.size();
        if (headerRow.size() < n) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "表头不匹配，请下载最新模板");
        }
        for (int i = 0; i < n; i++) {
            String cell = headerRow.get(i) == null ? "" : headerRow.get(i).trim();
            if (!cell.equals(expected.get(i))) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "表头不匹配，请下载最新模板（第 " + (i + 1) + " 列应为「" + expected.get(i) + "」）");
            }
        }
    }
}
