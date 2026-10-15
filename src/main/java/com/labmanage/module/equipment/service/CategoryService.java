package com.labmanage.module.equipment.service;

import com.labmanage.module.equipment.dto.CategoryCreateRequest;
import com.labmanage.module.equipment.dto.CategoryUpdateRequest;
import com.labmanage.module.equipment.vo.CategoryTreeVO;

import java.util.List;

/**
 * 设备分类服务（多级树）
 */
public interface CategoryService {

    /**
     * 分类树（全量，含停用节点；前端按 enabled 标识）
     */
    List<CategoryTreeVO> tree();

    /**
     * 新增分类（编码唯一，父节点须存在）
     */
    void create(CategoryCreateRequest request);

    /**
     * 修改分类（禁止层级循环由服务端校验）
     */
    void update(String id, CategoryUpdateRequest request);

    /**
     * 删除分类（逻辑删除；有子分类或型号时拒绝）
     */
    void delete(String id);
}
