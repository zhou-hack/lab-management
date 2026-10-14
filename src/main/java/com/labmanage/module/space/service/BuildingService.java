package com.labmanage.module.space.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.space.dto.BuildingCreateRequest;
import com.labmanage.module.space.dto.BuildingPageQuery;
import com.labmanage.module.space.dto.BuildingUpdateRequest;
import com.labmanage.module.space.vo.BuildingOptionVO;
import com.labmanage.module.space.vo.BuildingVO;

import java.util.List;

/**
 * 楼栋服务
 */
public interface BuildingService {

    /**
     * 楼栋分页查询
     */
    PageResult<BuildingVO> page(BuildingPageQuery query);

    /**
     * 楼栋下拉选项（启用中，含校区名）
     */
    List<BuildingOptionVO> options();

    /**
     * 新增楼栋
     */
    void create(BuildingCreateRequest request);

    /**
     * 修改楼栋
     */
    void update(String id, BuildingUpdateRequest request);

    /**
     * 删除楼栋（逻辑删除；有子实验室或数据范围引用时拒绝）
     */
    void delete(String id);
}
