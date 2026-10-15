package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialCategoryCreateDTO;
import com.labmanage.module.item.dto.MaterialCategoryQuery;
import com.labmanage.module.item.dto.MaterialCategoryUpdateDTO;
import com.labmanage.module.item.vo.MaterialCategoryVO;

import java.util.List;

/**
 * 物料分类服务
 */
public interface MaterialCategoryService {

    /** 分页查询 */
    PageResult<MaterialCategoryVO> page(MaterialCategoryQuery query);

    /** 启用分类列表（供下拉选择） */
    List<MaterialCategoryVO> listEnabled();

    /** 详情 */
    MaterialCategoryVO get(String id);

    /** 新增分类 */
    void create(MaterialCategoryCreateDTO dto);

    /** 修改分类 */
    void update(MaterialCategoryUpdateDTO dto);

    /** 启用/停用 */
    void updateStatus(String id, boolean enabled);

    /** 删除（逻辑删除；存在子分类或被物料引用时拒绝） */
    void delete(String id);
}
