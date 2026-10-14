package com.labmanage.roles.menu.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 菜单实体（动态路由来源）
 */
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 父菜单 ID（NULL=根节点） */
    private Long parentId;

    /** 菜单编码（唯一） */
    private String menuCode;

    /** 菜单名称 */
    private String menuName;

    /** 路由地址（目录/页面有值） */
    private String routePath;

    /** 前端组件键（仅可对应前端白名单） */
    private String componentKey;

    /** 绑定权限（→ sys_permission.id，动作类必填） */
    private Long permissionId;

    /** 类型：DIRECTORY/PAGE/ACTION */
    private String menuType;

    /** 同级排序（升序） */
    private Integer sortOrder;

    /** 启用：1正常 0停用 */
    private Integer enabled;

    /** 绑定权限标识（非表字段，查询时关联 sys_permission 带出） */
    @TableField(exist = false)
    private String permissionCode;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getMenuCode() {
        return menuCode;
    }

    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public void setComponentKey(String componentKey) {
        this.componentKey = componentKey;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }
}
