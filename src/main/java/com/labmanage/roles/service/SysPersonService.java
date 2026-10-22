package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.PersonBatchDepartmentDTO;
import com.labmanage.roles.dto.PersonPageQueryDTO;
import com.labmanage.roles.vo.PersonPageItemVO;

/**
 * 统一人员视图服务
 */
public interface SysPersonService {

    PageResult<PersonPageItemVO> page(PersonPageQueryDTO query);

    /**
     * 批量修改部门：名单行与账号行可混合，整批事务；要求目标部门启用，
     * 且调用方对目标部门与每一行的原部门均有写范围（运维管理员豁免），返回更新行数。
     */
    int batchUpdateDepartment(PersonBatchDepartmentDTO dto);
}
