package com.labmanage.module.equipment.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.equipment.dto.ModelCreateRequest;
import com.labmanage.module.equipment.dto.ModelPageQuery;
import com.labmanage.module.equipment.dto.ModelUpdateRequest;
import com.labmanage.module.equipment.vo.ModelVO;

/**
 * 设备型号服务
 */
public interface ModelService {

    /**
     * 型号分页查询（关键字匹配型号码/名称/品牌）
     */
    PageResult<ModelVO> page(ModelPageQuery query);

    /**
     * 新增型号（型号码唯一，所属分类须存在且启用）
     */
    void create(ModelCreateRequest request);

    /**
     * 修改型号（型号码不可改）
     */
    void update(String id, ModelUpdateRequest request);

    /**
     * 删除型号（逻辑删除；有台账设备时拒绝）
     */
    void delete(String id);
}
