package com.labmanage.roles.vo;

import java.util.List;

/**
 * 菜单节点（树形）
 */
public record MenuVO(
        String id,
        String parentId,
        String menuCode,
        String menuName,
        String routePath,
        String componentKey,
        String menuType,
        Integer sortOrder,
        List<MenuVO> children
) {
}
