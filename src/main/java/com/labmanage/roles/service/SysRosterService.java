package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.RosterBatchCreateDTO;
import com.labmanage.roles.dto.RosterCreateDTO;
import com.labmanage.roles.dto.RosterEnabledDTO;
import com.labmanage.roles.dto.RosterPageQueryDTO;
import com.labmanage.roles.dto.RosterUpdateDTO;
import com.labmanage.roles.vo.RosterPageItemVO;

/**
 * 预录名单管理服务
 */
public interface SysRosterService {

    /** 名单分页列表（关键字/身份类型/建档状态筛选） */
    PageResult<RosterPageItemVO> page(RosterPageQueryDTO query);

    /** 单条录入；学工号/手机号冲突抛 40900 */
    void create(RosterCreateDTO dto);

    /** 批量导入（同事务，任一条冲突整体回滚并报行号） */
    int createBatch(RosterBatchCreateDTO dto);

    /** 编辑名单（学工号、身份类型不可改）；已建档名单禁止编辑 */
    void update(Long id, RosterUpdateDTO dto);

    /** 启用/停用；已建档名单禁止停用 */
    void updateEnabled(Long id, RosterEnabledDTO dto);
}
