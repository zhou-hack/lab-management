package com.labmanage.module.space.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.space.dto.CampusCreateDTO;
import com.labmanage.module.space.dto.CampusPageQueryDTO;
import com.labmanage.module.space.dto.CampusUpdateDTO;
import com.labmanage.module.space.vo.CampusOptionVO;
import com.labmanage.module.space.vo.CampusPageItemVO;

import java.util.List;

/**
 * 校区管理服务
 */
public interface SpaceCampusService {

    PageResult<CampusPageItemVO> page(CampusPageQueryDTO query);

    /** 启用校区下拉选项 */
    List<CampusOptionVO> options();

    void create(CampusCreateDTO dto);

    /** 编辑校区（编码不可改） */
    void update(Long id, CampusUpdateDTO dto);

    /** 启用/停用校区（存在启用楼栋时禁止停用） */
    void updateEnabled(Long id, Integer enabled);
}
