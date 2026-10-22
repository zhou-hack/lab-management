package com.labmanage.roles.service;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.GrantCreateDTO;
import com.labmanage.roles.vo.GrantVO;
import com.labmanage.roles.vo.StudentOptionVO;

import java.util.List;

/**
 * 助理授权服务：老师管理本人名下的学生助理授权（代办物料领还）。
 * ASSISTANT 附加角色不持久化，由有效授权（ACTIVE 且时间窗内）动态计算。
 */
public interface AssistantGrantService {

    /**
     * 本人授权的助理分页列表（按创建时间倒序，过期授权状态派生为 EXPIRED）。
     */
    PageResult<GrantVO> pageMine(BasePageQuery query);

    /**
     * 按学号/姓名模糊搜索可授权学生（已注册且启用中的 STUDENT 用户，最多 20 条）。
     */
    List<StudentOptionVO> searchStudents(String keyword);

    /**
     * 创建授权：即时生效；同一老师对同一学生仅允许一条生效中授权。
     */
    Long create(GrantCreateDTO dto);

    /**
     * 撤销授权：仅本人可操作，且仅生效中授权可撤销。
     */
    void revoke(Long id);
}
