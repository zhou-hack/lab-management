package com.labmanage.module.space.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.space.dto.LabCreateRequest;
import com.labmanage.module.space.dto.LabPageQuery;
import com.labmanage.module.space.dto.LabUpdateRequest;
import com.labmanage.module.space.vo.LabOptionVO;
import com.labmanage.module.space.vo.LabVO;

import java.util.List;

/**
 * 实验室服务
 */
public interface LabService {

    /**
     * 实验室分页查询（含楼栋/校区/部门信息）
     */
    PageResult<LabVO> page(LabPageQuery query);

    /**
     * 实验室下拉选项（启用中，含楼栋/校区名）
     */
    List<LabOptionVO> options();

    /**
     * 新增实验室
     */
    void create(LabCreateRequest request);

    /**
     * 修改实验室
     */
    void update(String id, LabUpdateRequest request);

    /**
     * 删除实验室（逻辑删除；有设备或数据范围引用时拒绝）
     */
    void delete(String id);
}
