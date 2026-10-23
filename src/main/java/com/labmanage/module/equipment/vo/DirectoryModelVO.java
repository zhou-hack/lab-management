package com.labmanage.module.equipment.vo;

import java.util.List;

/**
 * 设备目录聚合视图对象（按型号合并：在架台数 + 借出中资产编号 + 首个在架资产预选）。
 * 用于学生/教师端设备目录：相同型号设备合并展示，不逐台暴露在架资产编号；
 * 借出中设备显示其资产编号，方便知悉占用。
 */
public record DirectoryModelVO(
        String modelId,
        String modelCode,
        String equipmentName,
        String categoryId,
        String categoryName,
        long idleCount,
        List<String> checkedOutCodes,
        String firstIdleAssetId,
        String firstIdleAssetCode
) {
}
