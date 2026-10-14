package com.labmanage.module.space.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.space.dto.CampusCreateRequest;
import com.labmanage.module.space.dto.CampusPageQuery;
import com.labmanage.module.space.dto.CampusUpdateRequest;
import com.labmanage.module.space.vo.CampusVO;

/**
 * 校区服务
 */
public interface CampusService {

    /**
     * 校区分页查询
     */
    PageResult<CampusVO> page(CampusPageQuery query);

    /**
     * 新增校区
     */
    void create(CampusCreateRequest request);

    /**
     * 修改校区
     */
    void update(String id, CampusUpdateRequest request);

    /**
     * 删除校区（逻辑删除；有子楼栋或数据范围引用时拒绝）
     */
    void delete(String id);
}
