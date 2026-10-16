package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.GrantCreateDTO;
import com.labmanage.roles.dto.GrantPageQueryDTO;
import com.labmanage.roles.vo.GrantPageItemVO;
import com.labmanage.roles.vo.StudentOptionVO;

import java.util.List;

/**
 * 助理授权服务：ASSISTANT 附加角色不持久化，由有效授权动态计算。
 */
public interface AssistantGrantService {

    /** 我的授权分页（当前登录老师） */
    PageResult<GrantPageItemVO> pageMine(GrantPageQueryDTO query);

    /** 授权审计分页（管理员，全量） */
    PageResult<GrantPageItemVO> pageAudit(GrantPageQueryDTO query);

    /** 创建授权：仅老师授权有效学生，最长一学年（366 天） */
    void create(GrantCreateDTO dto);

    /** 撤销授权：仅本人且 ACTIVE 的授权可撤销 */
    void revoke(Long id);

    /** 被授权学生搜索（有效学生账号，最多 20 条） */
    List<StudentOptionVO> studentOptions(String keyword);
}
