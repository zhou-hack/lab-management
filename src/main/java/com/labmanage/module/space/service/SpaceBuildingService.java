package com.labmanage.module.space.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.space.dto.BuildingCreateDTO;
import com.labmanage.module.space.dto.BuildingPageQueryDTO;
import com.labmanage.module.space.dto.BuildingUpdateDTO;
import com.labmanage.module.space.vo.BuildingOptionVO;
import com.labmanage.module.space.vo.BuildingPageItemVO;

import java.util.List;

/**
 * 楼栋管理服务
 */
public interface SpaceBuildingService {

    PageResult<BuildingPageItemVO> page(BuildingPageQueryDTO query);

    /** 启用楼栋下拉选项（可按校区过滤） */
    List<BuildingOptionVO> options(Long campusId);

    void create(BuildingCreateDTO dto);

    /** 编辑楼栋（编码不可改；允许调整所属校区） */
    void update(Long id, BuildingUpdateDTO dto);

    /** 启用/停用楼栋（存在启用实验室时禁止停用） */
    void updateEnabled(Long id, Integer enabled);
}
