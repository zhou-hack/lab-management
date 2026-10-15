package com.labmanage.module.equipment.vo;

import java.util.List;

/**
 * 设备分类树视图对象
 */
public record CategoryTreeVO(
        String id,
        String parentId,
        String categoryCode,
        String categoryName,
        Integer enabled,
        List<CategoryTreeVO> children
) {
}
