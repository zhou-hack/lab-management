package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.PersonPageQueryDTO;
import com.labmanage.roles.vo.PersonPageItemVO;

/**
 * 统一人员视图服务（名单与账号合并，一行一人）
 */
public interface SysPersonService {

    /** 统一人员分页（名单 LEFT JOIN 账号 UNION 纯后台账号） */
    PageResult<PersonPageItemVO> page(PersonPageQueryDTO query);
}
