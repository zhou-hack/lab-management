package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 物料分类（树形）
 */
@TableName("material_category")
public class MaterialCategory extends BaseEntity {

    /** 父分类 ID（NULL=根节点） */
    private Long parentId;

    /** 分类编码（唯一，逻辑删除后不复用） */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
